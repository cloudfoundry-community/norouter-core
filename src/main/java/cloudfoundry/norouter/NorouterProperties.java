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

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;

/**
 * @author Mike Heath
 */
@ConfigurationProperties(prefix = "norouter")
public class NorouterProperties {

	private String hostAddress = detectHostAddress();

	private static String detectHostAddress() {
		try {
			final DatagramChannel channel = DatagramChannel.open().connect(new InetSocketAddress("8.8.8.8", 1));
			return ((InetSocketAddress)channel.getLocalAddress()).getHostString();
		} catch (IOException e) {
			return null;
		}
	}

	public String getHostAddress() {
		return hostAddress;
	}

	public void setHostAddress(String hostAddress) {
		this.hostAddress = hostAddress;
	}
}
