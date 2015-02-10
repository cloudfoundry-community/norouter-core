package cloudfoundry.norouter.routingtable;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public interface RouteRegistrar {

	void registerRoute(String host, InetSocketAddress address, UUID applicationGuid, Integer applicationIndex, String privateInstanceId);

}
