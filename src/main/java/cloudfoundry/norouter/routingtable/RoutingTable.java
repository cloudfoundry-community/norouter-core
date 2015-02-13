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
package cloudfoundry.norouter.routingtable;

import cloudfoundry.norouter.RouteProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Mike Heath
 */
// TODO Create an ApplicationEventPublisher class that is backed by a thread pool
// TODO Add unregisterRoute method
public class RoutingTable implements AutoCloseable, RouteRegistrar {

	private final ApplicationEventPublisher eventPublisher;

	private final ScheduledFuture<?> staleRouteScheduleFuture;
	private final Duration staleRouteTimeout;

	// Access to #routeTable must be synchronized on #lock.
	private final Map<String, Map<SocketAddress, Route>> routeTable = new HashMap<>();
	private final Object lock = new Object();
	private final List<RouteProvider> routeProviders;

	public RoutingTable(ApplicationEventPublisher eventPublisher, Duration staleRouteTimeout, RouteProvider... routeProviders) {
		this(eventPublisher, null, staleRouteTimeout, routeProviders);
	}

	public RoutingTable(ApplicationEventPublisher eventPublisher, ScheduledExecutorService scheduler, Duration staleRouteTimeout, RouteProvider... routeProviders) {
		this.eventPublisher = eventPublisher;
		this.staleRouteTimeout = staleRouteTimeout;
		this.routeProviders = Arrays.asList(routeProviders);

		staleRouteScheduleFuture =
			(scheduler == null ) ? null : scheduler.scheduleAtFixedRate(
				this::cleanupStaleRoutes,
				staleRouteTimeout.toMillis(),
				staleRouteTimeout.toMillis(),
				TimeUnit.MILLISECONDS
			);
	}

	@Override
	public void close() {
		if (staleRouteScheduleFuture != null) {
			staleRouteScheduleFuture.cancel(true);
		}
	}

	protected int cleanupStaleRoutes() {
		boolean available = true;
		for (RouteProvider provider : routeProviders) {
			available &= provider.isAvailable();
		}
		if (!available) {
			return 0;
		}
		final Instant now = Instant.now();
		int count = 0;
		synchronized (lock) {
			final Iterator<Map.Entry<String, Map<SocketAddress, Route>>> routeTableIterator = routeTable.entrySet().iterator();
			while (routeTableIterator.hasNext()) {
				final Map.Entry<String, Map<SocketAddress, Route>> routeTableEntry = routeTableIterator.next();
				final Map<SocketAddress, Route> routeMap = routeTableEntry.getValue();
				final Iterator<Map.Entry<SocketAddress, Route>> routeMapIterator = routeMap.entrySet().iterator();
				while (routeMapIterator.hasNext()) {
					final Map.Entry<SocketAddress, Route> routeEntry = routeMapIterator.next();
					final Duration staleTime = Duration.between(routeEntry.getValue().lasteUpdated, now);
					if (staleTime.compareTo(staleRouteTimeout) > 0) {
						count++;
						publishRouteUnregister(routeEntry.getValue());
						routeMapIterator.remove();
					}
				}
				if (routeMap.isEmpty()) {
					routeTableIterator.remove();
				}
			}
		}
		return count;
	}

	@Override
	public void registerRoute(String host, InetSocketAddress address, UUID applicationGuid, Integer applicationIndex, String privateInstanceId) {
		final Route newRoute = new Route(address, applicationGuid, applicationIndex, host, privateInstanceId);
		synchronized (lock) {
			Map<SocketAddress, Route> routes = routeTable.get(host);
			if (routes == null) {
				routes = new HashMap<>();
				routeTable.put(host, routes);
			}
			final Route route = routes.get(address);
			if (route == null) {
				routes.put(address, newRoute);
				publishRouteRegister(newRoute);
			} else if (!newRoute.equals(route)) {
				publishRouteRegister(newRoute);
				routes.put(address, newRoute);
			} else {
				route.touch();
			}
		}
	}

	private void publishRouteRegister(Route route) {
		eventPublisher.publishEvent(RouteRegisterEvent.fromRouteDetails(this, route));
	}

	private void publishRouteUnregister(Route route) {
		eventPublisher.publishEvent(RouteUnregisterEvent.fromRouteDetails(this, route));
	}

	public Set<RouteDetails> getRoutes(String host) {
		final HashSet<RouteDetails> routes = new HashSet<>();
		synchronized (lock) {
			final Map<SocketAddress, Route> routeMap = routeTable.get(host);
			if (routeMap != null) {
				routeMap.forEach((k, v) -> routes.add(v));
			}
		}
		return routes;
	}

	private class Route implements RouteDetails {
		private final InetSocketAddress address;
		private final UUID applicationGuid;
		private final Integer applicationIndex;
		private final String host;
		private final String privateInstanceId;
		private volatile Instant lasteUpdated;

		private Route(InetSocketAddress address, UUID applicationGuid, Integer applicationIndex, String host, String privateInstanceId) {
			this.address = address;
			this.applicationGuid = applicationGuid;
			this.applicationIndex = applicationIndex;
			this.host = host;
			this.privateInstanceId = privateInstanceId;
			touch();
		}

		@Override
		public InetSocketAddress getAddress() {
			return address;
		}

		@Override
		public UUID getApplicationGuid() {
			return applicationGuid;
		}

		public Integer getApplicationIndex() {
			return applicationIndex;
		}

		@Override
		public String getHost() {
			return host;
		}

		@Override
		public String getPrivateInstanceId() {
			return privateInstanceId;
		}

		public void touch() {
			lasteUpdated = Instant.now();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Route route = (Route) o;

			if (!address.equals(route.address)) return false;
			if (applicationGuid != null ? !applicationGuid.equals(route.applicationGuid) : route.applicationGuid != null)
				return false;
			if (privateInstanceId != null ? !privateInstanceId.equals(route.privateInstanceId) : route.privateInstanceId != null)
				return false;

			return true;
		}

		@Override
		public int hashCode() {
			int result = address.hashCode();
			result = 31 * result + (applicationGuid != null ? applicationGuid.hashCode() : 0);
			result = 31 * result + host.hashCode();
			result = 31 * result + (privateInstanceId != null ? privateInstanceId.hashCode() : 0);
			return result;
		}
	}
}
