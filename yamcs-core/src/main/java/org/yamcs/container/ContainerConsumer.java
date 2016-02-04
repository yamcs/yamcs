package org.yamcs.container;


import org.yamcs.ContainerExtractionResult;

public interface ContainerConsumer {
    void processContainer(ContainerExtractionResult cer);
}
