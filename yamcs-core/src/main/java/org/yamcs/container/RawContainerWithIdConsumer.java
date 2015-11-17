package org.yamcs.container;

import org.yamcs.ContainerExtractionResult;


public interface RawContainerWithIdConsumer {
	void processContainer(ContainerWithId cwi, ContainerExtractionResult cer);
}

