/*
 *   Copyright (c) 2015 Intellectual Reserve, Inc.  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package cloudfoundry.norouter.routingtable;

import org.springframework.context.ApplicationEvent;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Mike Heath
 */
public abstract class AbstractRouteEvent extends ApplicationEvent implements RouteDetails {

	private final InetSocketAddress address;
	private final UUID applicationGuid;
	private final Integer applicationIndex;
	private final String host;
	private final String privateInstanceId;

	protected AbstractRouteEvent(
			Object source,
			String host,
			InetSocketAddress address,
			UUID applicationGuid,
			Integer applicationIndex,
			String privateInstanceId) {
		super(source);
		Objects.requireNonNull(host, "host can NOT be null");
		Objects.requireNonNull(address, "address can NOT be null");
		this.host = host;
		this.address = address;
		this.applicationGuid = applicationGuid;
		this.applicationIndex = applicationIndex;
		this.privateInstanceId = privateInstanceId;
	}

	@Override
	public InetSocketAddress getAddress() {
		return address;
	}

	@Override
	public UUID getApplicationGuid() {
		return applicationGuid;
	}

	@Override
	public Integer getApplicationIndex() {
		return applicationIndex;
	}

	public String getHost() {
		return host;
	}

	public String getPrivateInstanceId() {
		return privateInstanceId;
	}
}
