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

import cf.nats.CfNats;
import cf.nats.DefaultCfNats;
import cf.nats.message.RouterRegister;
import cf.nats.message.RouterStart;
import cf.nats.message.RouterUnregister;
import cloudfoundry.norouter.routingtable.RouteRegistrar;
import nats.client.MockNats;
import nats.client.spring.NatsServerReadyApplicationEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.testng.annotations.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * @author Mike Heath
 */
public class NatsRouteProviderTest {

	@Test
	public void notAvailableBeforeStart() throws Exception {
		final UUID routerId = UUID.randomUUID();
		final MockNats mockNats = new MockNats();
		final CfNats nats = new DefaultCfNats(mockNats);
		final RouteRegistrar routeRegistrar = mock(RouteRegistrar.class);
		try (final NatsRouteProvider natsRouteProvider = NatsRouteProvider.create()
				.nats(nats)
				.routerId(routerId)
				.routeRegistrar(routeRegistrar)
				.natsPingInterval(Duration.ofMillis(10))
				.build()) {
			assertFalse(natsRouteProvider.isStarted());
			assertFalse(natsRouteProvider.isAvailable());

			natsRouteProvider.start();
			Thread.sleep(50); // Give the scheduled NATS message time to publish
			assertTrue(natsRouteProvider.isStarted());
			assertTrue(natsRouteProvider.isAvailable());
		} finally {
			mockNats.close();
		}
	}

	@Test
	public void publishesRouterStartOnStart() {
		final CountDownLatch routerStartLatch = new CountDownLatch(1);

		final UUID routerId = UUID.randomUUID();

		final MockNats mockNats = new MockNats();
		final CfNats nats = new DefaultCfNats(mockNats);

		final Duration registerInterval = Duration.ofSeconds(10);
		final String host = "10.1.2.3";

		// Assert router start message
		nats.subscribe(RouterStart.class, publication -> {
			final RouterStart routerStart = publication.getMessageBody();
			assertNotNull(routerStart);
			assertEquals(routerStart.getId(), routerId.toString());
			assertEquals(routerStart.getHosts().get(0), host);
			assertEquals(routerStart.getMinimumRegisterIntervalInSeconds().longValue(), registerInterval.getSeconds());
			routerStartLatch.countDown();
		});

		final RouteRegistrar routeRegistrar = mock(RouteRegistrar.class);
		try (
				final NatsRouteProvider natsRouteProvider = NatsRouteProvider.create()
				.nats(nats)
				.routerId(routerId)
				.routeRegistrar(routeRegistrar)
				.registerInterval(registerInterval)
				.addHost(host)
				.build()) {
			assertFalse(natsRouteProvider.isStarted());
			assertEquals(routerStartLatch.getCount(), 1);

			natsRouteProvider.start();
			assertTrue(natsRouteProvider.isStarted());
			assertEquals(routerStartLatch.getCount(), 0);
		} finally {
			mockNats.close();
		}
	}

	@Test
	public void registersRouteWithRouterRegisterMessage() {
		final RouteRegistrar routeRegistrar = mock(RouteRegistrar.class);

		final String host = "10.9.8.7";
		final int port = 412;
		final String uri1 = "some.address.com";
		final String uri2 = "some.other.address.com";

		final MockNats mockNats = new MockNats();
		final CfNats nats = new DefaultCfNats(mockNats);
		try (final NatsRouteProvider natsRouteProvider = NatsRouteProvider.create()
				.nats(nats)
				.routeRegistrar(routeRegistrar)
				.build()) {
			natsRouteProvider.start();
			nats.publish(new RouterRegister(host, port, uri1, uri2));
		}

		verify(routeRegistrar).registerRoute(uri1, InetSocketAddress.createUnresolved(host, port), null, null, null);
		verify(routeRegistrar).registerRoute(uri2, InetSocketAddress.createUnresolved(host, port), null, null, null);
	}

	@Test
	public void unregistersRouteWithRouterUnregisterMessage() {
		final RouteRegistrar routeRegistrar = mock(RouteRegistrar.class);

		final String host = "8.8.8.8";
		final int port = 4321;
		final String uri1 = "nolongerused.address.com";
		final String uri2 = "some.other.address";

		final MockNats mockNats = new MockNats();
		final CfNats nats = new DefaultCfNats(mockNats);
		try (final NatsRouteProvider natsRouteProvider = NatsRouteProvider.create()
				.nats(nats)
				.routeRegistrar(routeRegistrar)
				.build()) {
			natsRouteProvider.start();
			nats.publish(new RouterUnregister(host, port, uri1, uri2));
		}

		verify(routeRegistrar).unregisterRoute(uri1, InetSocketAddress.createUnresolved(host, port));
		verify(routeRegistrar).unregisterRoute(uri2, InetSocketAddress.createUnresolved(host, port));
	}

	@Test
	public void contextRefreshCausesStart() {
		final MockNats mockNats = new MockNats();
		final CfNats nats = new DefaultCfNats(mockNats);
		try (final NatsRouteProvider natsRouteProvider = NatsRouteProvider.create()
				.nats(nats)
				.routeRegistrar(mock(RouteRegistrar.class))
				.build()) {
			natsRouteProvider.onApplicationEvent(new ContextRefreshedEvent(mock(ApplicationContext.class)));
			assertTrue(natsRouteProvider.isStarted());
		}
	}

	@Test
	public void onNatsServerConnectionPublishRouterStart() {
		final AtomicInteger routerStartCounter = new AtomicInteger();
		final MockNats mockNats = new MockNats();
		mockNats.subscribe("router.start", message -> {
			routerStartCounter.incrementAndGet();
		});
		final CfNats nats = new DefaultCfNats(mockNats);
		try (final NatsRouteProvider natsRouteProvider = NatsRouteProvider.create()
				.nats(nats)
				.routeRegistrar(mock(RouteRegistrar.class))
				.build()) {
			// Router greet gets published at start time
			natsRouteProvider.start();
			assertEquals(routerStartCounter.get(), 1);

			// Router greet gets published when NATS client reconnects
			natsRouteProvider.onApplicationEvent(new NatsServerReadyApplicationEvent(mockNats));
			assertEquals(routerStartCounter.get(), 2);
		}
	}
}
