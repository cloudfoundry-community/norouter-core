package cloudfoundry.norouter.routingtable;

import org.springframework.context.ApplicationEvent;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public abstract class AbstractRouteEvent extends ApplicationEvent implements RouteDetails {

	private final InetSocketAddress address;
	private final UUID applicationGuid;
	private final String host;
	private final String privateInstanceId;

	protected AbstractRouteEvent(Object source, InetSocketAddress address, UUID applicationGuid, String host, String privateInstanceId) {
		super(source);
		this.address = address;
		this.applicationGuid = applicationGuid;
		this.host = host;
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

	public String getHost() {
		return host;
	}

	public String getPrivateInstanceId() {
		return privateInstanceId;
	}
}
