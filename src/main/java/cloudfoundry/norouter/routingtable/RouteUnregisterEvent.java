package cloudfoundry.norouter.routingtable;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public class RouteUnregisterEvent extends AbstractRouteEvent {

	public static RouteUnregisterEvent fromRouteDetails(Object source, RouteDetails route) {
		return new RouteUnregisterEvent(source, route.getHost(), route.getAddress(), route.getApplicationGuid(), route.getApplicationIndex(), route.getPrivateInstanceId());
	}

	public RouteUnregisterEvent(Object source, String host, InetSocketAddress address, UUID applicationGuid, Integer applicationIndex, String privateInstanceId) {
		super(source, host, address, applicationGuid, applicationIndex, privateInstanceId);
	}
}
