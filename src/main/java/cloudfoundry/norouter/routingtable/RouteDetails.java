package cloudfoundry.norouter.routingtable;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public interface RouteDetails {

	InetSocketAddress getAddress();

	UUID getApplicationGuid();

	Integer getApplicationIndex();

	String getHost();

	String getPrivateInstanceId();

}
