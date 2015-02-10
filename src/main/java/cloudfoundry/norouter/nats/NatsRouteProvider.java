package cloudfoundry.norouter.nats;

import cf.nats.CfNats;
import cf.nats.MessageBody;
import cf.nats.NatsSubject;
import cf.nats.message.RouterGreet;
import cf.nats.message.RouterRegister;
import cf.nats.message.RouterStart;
import cloudfoundry.norouter.RouteProvider;
import cloudfoundry.norouter.routingtable.RouteRegistrar;
import nats.client.Registration;
import nats.client.Subscription;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public class NatsRouteProvider implements AutoCloseable, RouteProvider {

	private final CfNats nats;
	private final Duration natsPingInterval;

	private volatile boolean started = false;

	private Subscription routerGreetSubscription;

	private Subscription pingSubscription;
	private volatile Instant lastPingReceipt;
	private RouterStart routerStartMessage;
	private Registration pingRegistration;

	public static Builder create() {
		return new Builder();
	}

	public static class Builder {

		private List<String> hosts = new ArrayList<>();
		private CfNats nats;
		private Duration natsPingInterval = null;
		private Duration registerInterval = Duration.ofSeconds(5);
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

		NatsRouteProvider build() {
			return new NatsRouteProvider(this);
		}

	}

	private NatsRouteProvider(Builder builder) {
		Objects.requireNonNull(builder.registrar, "routeRegistrar is a required argument");
		final RouteRegistrar registrar = builder.registrar;

		Objects.requireNonNull(builder.nats, "nats is a required argument");
		nats = builder.nats;
		natsPingInterval = (builder.natsPingInterval == null) ? builder.registerInterval : builder.natsPingInterval;

		nats.subscribe(RouterRegister.class, publication -> {
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

		// TODO Version field isn't used anymore, remove from cf-nats
		// TODO Refactor RouterStart to accept a Duration instance
		routerStartMessage = new RouterStart(
				builder.routerId.toString(),
				"1",
				builder.hosts,
				builder.registerInterval.getSeconds());
	}

	public void start() {
		nats.publish(routerStartMessage);
		routerGreetSubscription = nats.subscribe(RouterGreet.class, (message) -> message.reply(routerStartMessage));

		pingSubscription = nats.subscribe(PingMessage.class, (message) -> {
			System.out.println("blah");
			lastPingReceipt = Instant.now();
		});
		pingRegistration = nats.publish(new PingMessage(), natsPingInterval.toMillis(), TimeUnit.MILLISECONDS);

		started = true;
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
	private static class PingMessage implements MessageBody<Void> {
		public PingMessage() {
			// Provide public default constructor to enable instantiation by CfNats.
		}
	}
}
