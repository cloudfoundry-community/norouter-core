/*
 *   Copyright (c) 2015 Intellectual Reserve, Inc.  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
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
