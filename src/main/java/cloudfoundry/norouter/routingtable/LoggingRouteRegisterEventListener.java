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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

/**
 * @author Mike Heath
 */
public class LoggingRouteRegisterEventListener implements ApplicationListener<RouteRegisterEvent>, Ordered {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoggingRouteRegisterEventListener.class);

	private final int order;

	public LoggingRouteRegisterEventListener() {
		this(Integer.MIN_VALUE);
	}

	public LoggingRouteRegisterEventListener(int order) {
		this.order = order;
	}

	@Override
	public void onApplicationEvent(RouteRegisterEvent event) {
		LOGGER.info("Registering route {} with target address {}", event.getHost(), event.getAddress());
	}

	@Override
	public int getOrder() {
		return order;
	}
}
