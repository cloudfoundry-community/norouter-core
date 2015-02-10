package cloudfoundry.norouter.routingtable;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public class RouteRegisterEvent extends AbstractRouteEvent {

	public static RouteRegisterEvent fromRouteDetails(Object source, RouteDetails details) {
		return new RouteRegisterEvent(source, details.getHost(), details.getAddress(), details.getApplicationGuid(), details.getApplicationIndex(), details.getPrivateInstanceId());
	}

	public RouteRegisterEvent(Object source, String host, InetSocketAddress address, UUID applicationGuid, Integer applicationIndex, String privateInstanceId) {
		super(source, host, address, applicationGuid, applicationIndex, privateInstanceId);
	}
}
