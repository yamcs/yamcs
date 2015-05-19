package org.yamcs.container;

import java.nio.ByteBuffer;


public interface ContainerWithIdConsumer {
	void processContainer(ContainerWithId cwi, ByteBuffer content);
}

