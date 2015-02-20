/*
 * Copyright (c) 2015 Intellectual Reserve, Inc.  All rights reserved.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package cloudfoundry.norouter.nats;

import cf.common.JsonObject;
import cf.nats.CfNats;
import cf.nats.MessageBody;
import cf.nats.NatsSubject;
import cf.nats.message.RouterGreet;
import cf.nats.message.RouterRegister;
import cf.nats.message.RouterStart;
import cf.nats.message.RouterUnregister;
import cloudfoundry.norouter.RouteProvider;
import cloudfoundry.norouter.routingtable.RouteRegistrar;
import nats.client.Registration;
import nats.client.Subscription;
import nats.client.spring.NatsServerReadyApplicationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Mike Heath
 */
public class NatsRouteProvider implements AutoCloseable, RouteProvider, ApplicationListener<ApplicationEvent>, Ordered {

	private static final Logger LOGGER = LoggerFactory.getLogger(NatsRouteProvider.class);

	private final CfNats nats;
	private final Duration natsPingInterval;

	private volatile boolean started = false;

	private Subscription routerGreetSubscription;

	private Registration pingRegistration;
	private Subscription pingSubscription;
	private volatile Instant lastPingReceipt;

	private RouterStart routerStartMessage;

	private Subscription routeRegisterSubscription;
	private Subscription routeUnregisterSubscription;

	private final RouteRegistrar registrar;

	public static Builder create() {
		return new Builder();
	}

	public static class Builder {

		private List<String> hosts = new ArrayList<>();
		private CfNats nats;
		private Duration natsPingInterval = null;
		private Duration registerInterval = Duration.ofSeconds(30);
		private UUID routerId = UUID.randomUUID();
		private RouteRegistrar registrar;

		public Builder addHost(String host) {
			Objects.requireNonNull(routerId);
			hosts.add(host);
			return this;
		}

		public Builder nats(CfNats nats) {
			Objects.requireNonNull(nats);
			this.nats = nats;
			return this;
		}

		public Builder natsPingInterval(Duration natsPingInterval) {
			this.natsPingInterval = natsPingInterval;
			return this;
		}

		public Builder registerInterval(Duration registerInterval) {
			Objects.requireNonNull(registerInterval);
			this.registerInterval = registerInterval;
			return this;
		}

		public Builder routerId(UUID routerId) {
			Objects.requireNonNull(routerId);
			this.routerId = routerId;
			return this;
		}

		public Builder routeRegistrar(RouteRegistrar registrar) {
			Objects.requireNonNull(registrar);
			this.registrar = registrar;
			return this;
		}

		public NatsRouteProvider build() {
			return new NatsRouteProvider(this);
		}

	}

	private NatsRouteProvider(Builder builder) {
		Objects.requireNonNull(builder.registrar, "routeRegistrar is a required argument");
		registrar = builder.registrar;

		Objects.requireNonNull(builder.nats, "nats is a required argument");
		nats = builder.nats;
		natsPingInterval = (builder.natsPingInterval == null) ? builder.registerInterval : builder.natsPingInterval;

		// TODO Version field isn't used anymore, remove from cf-nats
		// TODO Refactor RouterStart to accept a Duration instance
		routerStartMessage = new RouterStart(
				builder.routerId.toString(),
				"1",
				builder.hosts,
				builder.registerInterval.getSeconds());
	}

	public void start() {
		pingSubscription = nats.subscribe(PingMessage.class, (message) -> lastPingReceipt = Instant.now());
		pingRegistration = nats.publish(new PingMessage(), natsPingInterval.toMillis(), TimeUnit.MILLISECONDS);

		routeRegisterSubscription = nats.subscribe(RouterRegister.class, publication -> {
			final RouterRegister routerRegister = publication.getMessageBody();
			routerRegister.getUris().forEach(uri -> {
				final InetSocketAddress address = InetSocketAddress.createUnresolved(routerRegister.getHost(), routerRegister.getPort());
				registrar.registerRoute(
						uri,
						address,
						routerRegister.getApp() == null ? null : UUID.fromString(routerRegister.getApp()),
						routerRegister.getIndex(),
						routerRegister.getPrivateInstanceId());
			});
		});

		routeUnregisterSubscription = nats.subscribe(RouterUnregister.class, publication -> {
			final RouterUnregister routerUnregister = publication.getMessageBody();
			routerUnregister.getUris().forEach(uri -> {
				final InetSocketAddress address = InetSocketAddress.createUnresolved(routerUnregister.getHost(), routerUnregister.getPort());
				registrar.unregisterRoute(uri, address);
			});
		});

		routerGreetSubscription = nats.subscribe(RouterGreet.class, (message) -> message.reply(routerStartMessage));

		started = true;
		LOGGER.info("Listening for route updates over NATS");
	}

	public void close() {
		if (pingRegistration != null) {
			pingRegistration.remove();
		}
		if (pingSubscription != null) {
			pingSubscription.close();
		}
		if (routerGreetSubscription != null) {
			routerGreetSubscription.close();
		}
		if (routeRegisterSubscription != null) {
			routeRegisterSubscription.close();
		}
		if (routeUnregisterSubscription != null) {
			routeUnregisterSubscription.close();
		}
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof ContextRefreshedEvent) {
			start();
		} else if (event instanceof NatsServerReadyApplicationEvent) {
			nats.publish(routerStartMessage);
		}
	}

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public boolean isAvailable() {
		boolean available = started && nats.isConnected();
		final Instant lastPingTime = lastPingReceipt == null ? Instant.MIN : lastPingReceipt;
		final Duration timeSincePing = Duration.between(lastPingTime, Instant.now());
		available &= timeSincePing.compareTo(natsPingInterval.multipliedBy(2)) < 0;
		return available;
	}

	public boolean isStarted() {
		return started;
	}

	@NatsSubject("norouter.ping")
	private static class PingMessage extends JsonObject implements MessageBody<Void> {
		public PingMessage() {
			// Provide public default constructor to enable instantiation by CfNats.
		}
	}
}
