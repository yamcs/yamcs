package org.yamcs.container;

import java.nio.ByteBuffer;


public interface RawContainerWithIdConsumer {
	void processContainer(ContainerWithId cwi, ByteBuffer content);
}

