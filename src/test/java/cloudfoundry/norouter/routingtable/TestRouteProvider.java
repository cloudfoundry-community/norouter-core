package cloudfoundry.norouter.routingtable;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public class TestRouteProvider implements RouteProvider {

	@Override
	public boolean isAvailable() {
		return true;
	}

}
