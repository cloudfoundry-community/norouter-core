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
import cf.spring.PidFileFactory;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.concurrent.ScheduledExecutorFactoryBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

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

	@Bean @Order(Integer.MIN_VALUE)
	ThreadPoolTaskExecutor natsExecutor() {
		final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setMaxPoolSize(1);
		return executor;
	}

	@Bean
	@ConditionalOnProperty("pidfile")
	PidFileFactory pidFile(@Value("${pidfile}") String pidfile) throws IOException {
		return new PidFileFactory(pidfile);
	}

	@Bean
	QueuedEventPublisher queuedEventPublisher(ApplicationEventPublisher publisher) {
		return new QueuedEventPublisher(publisher);
	}

	@Bean
	Nats nats(
			QueuedEventPublisher eventPublisher,
			EventLoopGroup workerGroup,
			@Value("${nats.machines}") String[] natsMachines) {
		final NatsConnector natsConnector = new NatsBuilder(eventPublisher)
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
				Duration.ofMinutes(5), // TODO Make the stale route duration configurable
				compositeRouteProvider());
	}

	static class QueuedEventPublisher implements ApplicationEventPublisher, ApplicationListener<ContextRefreshedEvent>, Ordered {

		private final ApplicationEventPublisher publisher;
		private final Queue<ApplicationEvent> events = new LinkedList<>();
		private boolean started = false;

		QueuedEventPublisher(ApplicationEventPublisher publisher) {
			this.publisher = publisher;
		}

		@Override
		public synchronized void publishEvent(ApplicationEvent event) {
			if (started) {
				publisher.publishEvent(event);
			} else {
				events.add(event);
			}
		}

		@Override
		public synchronized void onApplicationEvent(ContextRefreshedEvent event) {
			if (!started) {
				started = true;
				events.forEach(this::publishEvent);
			}
		}

		@Override
		public int getOrder() {
			return Integer.MAX_VALUE;
		}
	}

	public static void main(String[] args) {
		new SpringApplicationBuilder().sources(Main.class).build().run(args);
	}

}
