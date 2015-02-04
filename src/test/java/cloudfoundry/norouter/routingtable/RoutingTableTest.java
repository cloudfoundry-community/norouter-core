package cloudfoundry.norouter.routingtable;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public class RoutingTableTest {

	private static final Duration STALE_ROUTE_TIMEOUT = Duration.of(500, ChronoUnit.MILLIS);
	private static ScheduledExecutorService scheduler;

	private static final String HOST = "foo.lds.org";
	private static final InetSocketAddress ADDRESS = InetSocketAddress.createUnresolved("1.2.3.4", 1234);
	private static final UUID APPLICATION_GUID = UUID.randomUUID();
	private static final String PRIVATE_INSTANCE_ID = "thisIsPrivate";

	private ApplicationEventQueue eventPublisher;
	private RoutingTable routingTable;

	@BeforeClass
	public void init() {
		scheduler = Executors.newSingleThreadScheduledExecutor();
	}

	@AfterClass
	public void shutdown() {
		scheduler.shutdownNow();
	}

	@BeforeMethod
	public void setup() {
		eventPublisher = new ApplicationEventQueue();
		routingTable = new RoutingTable(eventPublisher, scheduler, STALE_ROUTE_TIMEOUT);
	}

	@AfterMethod
	public void cleanup() {
		routingTable.close();
	}

	@Test
	public void tableHoldsRegisteredRoutes() {
		registerDefaultRoute();

		Set<RouteDetails> routes = routingTable.getRoutes(HOST);
		final Optional<RouteDetails> first = routes.stream().findFirst();
		assertTrue(first.isPresent());
		final RouteDetails defaultRoute = first.orElseThrow(() -> new AssertionError("Routing table empty"));
		assertDefaultRoute(defaultRoute);

		// Register second route
		final InetSocketAddress secondAddress = InetSocketAddress.createUnresolved("foo.lds.org", 54321);
		routingTable.registerRoute(HOST, secondAddress, null, null);
		routes = routingTable.getRoutes(HOST);
		assertEquals(routes.size(), 2);
		assertTrue(routes.remove(defaultRoute));
		final Optional<RouteDetails> second = routes.stream().findFirst();
		final RouteDetails secondRoute = second.orElseThrow(() -> new AssertionError("Did not add second route"));
		assertEquals(secondRoute.getAddress(), secondAddress);
	}

	@Test
	public void emitRouteRegisterEventOnRegisterNewRoute() {
		registerDefaultRoute();

		final RouteRegisterEvent applicationEvent = (RouteRegisterEvent) eventPublisher.poll();
		assertDefaultRoute(applicationEvent);
	}

	@Test
	public void doesNotEmitRouteRegisterEventOnRegisterExistingRoute() {
		registerDefaultRoute();
		registerDefaultRoute();

		final RouteRegisterEvent applicationEvent = (RouteRegisterEvent) eventPublisher.poll();
		assertDefaultRoute(applicationEvent);

		// Make sure there's only one event in the queue
		assertNull(eventPublisher.poll());
	}

	@Test
	public void emitRouteRegisterEventOnPrivateInstanceChange() {
		registerDefaultRoute();
		routingTable.registerRoute(HOST, ADDRESS, APPLICATION_GUID, null);

		final RouteRegisterEvent applicationEvent = (RouteRegisterEvent) eventPublisher.poll();
		assertDefaultRoute(applicationEvent);

		final RouteRegisterEvent secondEvent = (RouteRegisterEvent) eventPublisher.poll();
		assertNotNull(secondEvent);
		assertNull(secondEvent.getPrivateInstanceId());
	}

	@Test
	public void emitRouteRegisterEventOnAppGuidChange() {
		final UUID newAppGuid = UUID.randomUUID();
		registerDefaultRoute();
		routingTable.registerRoute(HOST, ADDRESS, newAppGuid, PRIVATE_INSTANCE_ID);

		final RouteRegisterEvent applicationEvent = (RouteRegisterEvent) eventPublisher.poll();
		assertDefaultRoute(applicationEvent);

		final RouteRegisterEvent secondEvent = (RouteRegisterEvent) eventPublisher.poll();
		assertNotNull(secondEvent);
		assertEquals(secondEvent.getApplicationGuid(), newAppGuid);
	}

	@Test
	public void unregisterRoutesAfterTimeout() throws Exception {
		final Duration staleRouteTimeout = Duration.ofMillis(100);
		routingTable = new RoutingTable(eventPublisher, staleRouteTimeout);
		registerDefaultRoute();

		// Make sure routes don't get cleaned up until they've expired
		assertEquals(routingTable.cleanupStaleRoutes(), 0);

		// Clear out route register event
		final RouteRegisterEvent registerEvent = (RouteRegisterEvent) eventPublisher.poll();
		assertDefaultRoute(registerEvent);

		Thread.sleep(staleRouteTimeout.toMillis() + 10);
		assertEquals(1, routingTable.cleanupStaleRoutes());

		// Ensure routes is empty
		final Set<RouteDetails> routes = routingTable.getRoutes(HOST);
		assertTrue(routes.isEmpty());

		// Ensure we have unregister event
		final RouteUnregisterEvent unregisterEvent = (RouteUnregisterEvent) eventPublisher.poll();
		assertDefaultRoute(unregisterEvent);
	}

	@Test
	public void doNotUnregisterRoutesWhileProviderDown() throws Exception {
		boolean[] available = { false };
		final Duration staleRouteTimeout = Duration.ofMillis(10);
		routingTable = new RoutingTable(eventPublisher, staleRouteTimeout, () -> available[0]);
		registerDefaultRoute();

		Thread.sleep(staleRouteTimeout.toMillis() + 10);
		assertEquals(0, routingTable.cleanupStaleRoutes());

		// With provider available, stale route is cleaned up
		available[0] = true;
		assertEquals(1, routingTable.cleanupStaleRoutes());

	}

	private void registerDefaultRoute() {
		routingTable.registerRoute(HOST, ADDRESS, APPLICATION_GUID, PRIVATE_INSTANCE_ID);
	}

	private void assertDefaultRoute(RouteDetails applicationEvent) {
		assertNotNull(applicationEvent);
		assertEquals(applicationEvent.getAddress(), ADDRESS);
		assertEquals(applicationEvent.getApplicationGuid(), APPLICATION_GUID);
		assertEquals(applicationEvent.getHost(), HOST);
		assertEquals(applicationEvent.getPrivateInstanceId(), PRIVATE_INSTANCE_ID);
	}

}
