package org.yamcs.container;

import java.util.List;

public interface ContainerWithIdConsumer {
	void update(int subscriptionId, List<ContainerValueWithId> containers);
}
