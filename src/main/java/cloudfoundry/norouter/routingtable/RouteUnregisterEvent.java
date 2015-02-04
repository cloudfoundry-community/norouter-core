package cloudfoundry.norouter.routingtable;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public class RouteUnregisterEvent extends AbstractRouteEvent {

	public static RouteUnregisterEvent fromRouteDetails(Object source, RouteDetails route) {
		return new RouteUnregisterEvent(source, route.getAddress(), route.getApplicationGuid(), route.getHost(), route.getPrivateInstanceId());
	}

	public RouteUnregisterEvent(Object source, InetSocketAddress address, UUID applicationGuid, String host, String privateInstanceId) {
		super(source, address, applicationGuid, host, privateInstanceId);
	}
}
