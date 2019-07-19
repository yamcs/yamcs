package org.yamcs.container;

import org.yamcs.ContainerExtractionResult;

public interface ContainerWithIdConsumer {

    void processContainer(ContainerWithId cwi, ContainerExtractionResult cer);
}
