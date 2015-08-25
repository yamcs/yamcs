package org.yamcs.container;

public interface ContainerWithIdConsumer {
	void processContainer(int subscriptionId, ContainerValueWithId container);
}
