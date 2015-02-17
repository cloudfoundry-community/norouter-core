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

package cloudfoundry.norouter;

import cf.nats.CfNats;
import cf.nats.DefaultCfNats;
import cf.spring.NettyEventLoopGroupFactoryBean;
import cloudfoundry.norouter.nats.NatsRouteProvider;
import cloudfoundry.norouter.routingtable.LoggingRouteRegisterEventListener;
import cloudfoundry.norouter.routingtable.LoggingRouteUnregisterEventListener;
import cloudfoundry.norouter.routingtable.RoutingTable;
import io.netty.channel.EventLoopGroup;
import nats.client.Nats;
import nats.client.NatsConnector;
import nats.client.spring.NatsBuilder;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.core.Ordered;
import org.springframework.scheduling.concurrent.ScheduledExecutorFactoryBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.Map;

/**
 * @author Mike Heath
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan("cloudfoundry.norouter.config")
public class Main {

	@Autowired
	private ListableBeanFactory beanFactory;

	@Bean
	LoggingRouteRegisterEventListener logRouteRegisterEvents() {
		return new LoggingRouteRegisterEventListener();
	}

	@Bean
	LoggingRouteUnregisterEventListener logRouteUnregisterEvents() {
		return new LoggingRouteUnregisterEventListener();
	}

	@Bean
	@Qualifier("worker")
	NettyEventLoopGroupFactoryBean workerGroup() {
		return new NettyEventLoopGroupFactoryBean();
	}

	@Bean
	ThreadPoolTaskExecutor natsExecutor() {
		final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setMaxPoolSize(1);
		return executor;
	}

	@Bean
	Nats nats(
			ApplicationEventPublisher publisher,
			EventLoopGroup workerGroup,
			@Value("${nats.machines}") String[] natsMachines) {
		final NatsConnector natsConnector = new NatsBuilder(publisher)
				.eventLoopGroup(workerGroup)
				.calllbackExecutor(natsExecutor());
		for (String natsMachine : natsMachines) {
			natsConnector.addHost(natsMachine);
		}
		return natsConnector.connect();
	}

	@Bean
	CfNats cfNats(Nats nats) {
		return new DefaultCfNats(nats);
	}

	@Bean
	NatsRouteProvider natsRouteProvider(RoutingTable routingTable, CfNats nats) {
		return NatsRouteProvider.create()
				// TODO Add host parameter
				.routeRegistrar(routingTable)
				.nats(nats)
				.build();
	}

	@Bean
	RouteProvider compositeRouteProvider() {
		return new RouteProvider() {
			@Override
			public boolean isAvailable() {
				final Map<String, RouteProvider> providerMap = beanFactory.getBeansOfType(RouteProvider.class);
				boolean available = true;
				for (RouteProvider provider : providerMap.values()) {
					if (provider == this) {
						continue;
					}
					available &= provider.isAvailable();
				}
				return available;
			}
		};
	}

	@Bean
	ScheduledExecutorFactoryBean staleRouteEvictionScheduledExecutor() {
		final ScheduledExecutorFactoryBean scheduledExecutorFactoryBean = new ScheduledExecutorFactoryBean();
		scheduledExecutorFactoryBean.setPoolSize(1);
		return scheduledExecutorFactoryBean;
	}

	@Bean
	RoutingTable routingTable(
			ApplicationEventPublisher publisher
	) {
		return new RoutingTable(
				publisher,
				staleRouteEvictionScheduledExecutor().getObject(),
				Duration.ofSeconds(30), // TODO Make the stale route duration configurable
				compositeRouteProvider());
	}

	@Bean
	NatsProviderStarter natsProviderStarter() {
		return new NatsProviderStarter();
	}

	/**
	 * It would be ideal if NatsRouteProvider itself listened for ContextStartedEvent. However, if
	 * the NATS client connects while the NatsRouteProvider is initializing (likely), it will cause a deadlock.
	 */
	static class NatsProviderStarter implements ApplicationListener<ContextStartedEvent>, Ordered {

		@Autowired
		NatsRouteProvider provider;

		@Override
		public void onApplicationEvent(ContextStartedEvent event) {
			provider.start();
		}

		@Override
		public int getOrder() {
			return 0;
		}
	}

	public static void main(String[] args) {
		new SpringApplicationBuilder().sources(Main.class).build().run(args).start();
	}
}
