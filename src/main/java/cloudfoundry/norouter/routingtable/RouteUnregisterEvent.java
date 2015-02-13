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

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * @author Mike Heath
 */
public class RouteUnregisterEvent extends AbstractRouteEvent {

	public static RouteUnregisterEvent fromRouteDetails(Object source, RouteDetails route) {
		return new RouteUnregisterEvent(source, route.getHost(), route.getAddress(), route.getApplicationGuid(), route.getApplicationIndex(), route.getPrivateInstanceId());
	}

	public RouteUnregisterEvent(Object source, String host, InetSocketAddress address, UUID applicationGuid, Integer applicationIndex, String privateInstanceId) {
		super(source, host, address, applicationGuid, applicationIndex, privateInstanceId);
	}
}
