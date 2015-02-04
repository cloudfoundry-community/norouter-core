package cloudfoundry.norouter.routingtable;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public class RouteRegisterEvent extends AbstractRouteEvent {

	public static RouteRegisterEvent fromRouteDetails(Object source, RouteDetails details) {
		return new RouteRegisterEvent(source, details.getAddress(), details.getApplicationGuid(), details.getHost(), details.getPrivateInstanceId());
	}

	public RouteRegisterEvent(Object source, InetSocketAddress address, UUID applicationGuid, String host, String privateInstanceId) {
		super(source, address, applicationGuid, host, privateInstanceId);
	}
}
