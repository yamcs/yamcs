package org.yamcs.container;

import java.nio.ByteBuffer;

import org.yamcs.xtce.SequenceContainer;

public interface ContainerConsumer {
    void processContainer(SequenceContainer sc, ByteBuffer content);
}
