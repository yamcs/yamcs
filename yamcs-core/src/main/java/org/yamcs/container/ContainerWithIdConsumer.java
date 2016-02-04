package org.yamcs.container;

import java.nio.ByteBuffer;

import org.yamcs.ContainerExtractionResult;


public interface ContainerWithIdConsumer {
	void processContainer(ContainerWithId cwi, ContainerExtractionResult cer);
}

