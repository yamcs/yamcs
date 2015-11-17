package org.yamcs.container;


import org.yamcs.ContainerExtractionResult;

public interface RawContainerConsumer {
    void processContainer(ContainerExtractionResult cer);
}
