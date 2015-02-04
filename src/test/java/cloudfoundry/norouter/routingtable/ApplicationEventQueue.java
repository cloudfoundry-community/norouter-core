package cloudfoundry.norouter.routingtable;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author Mike Heath <elcapo@gmail.com>
 */
public class ApplicationEventQueue implements ApplicationEventPublisher {

	private final Queue<ApplicationEvent> queue = new LinkedList<ApplicationEvent>();

	@Override
	public void publishEvent(ApplicationEvent event) {
		queue.add(event);
	}

	public ApplicationEvent poll() {
		return queue.poll();
	}

}
