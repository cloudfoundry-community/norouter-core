package cloudfoundry.norouter.routingtable;

import org.springframework.context.ApplicationEvent;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Mike Heath <elcapo@gmail.com>
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
