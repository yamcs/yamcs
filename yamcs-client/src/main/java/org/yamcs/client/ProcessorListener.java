package org.yamcs.client;

import org.yamcs.protobuf.ProcessorInfo;

public interface ProcessorListener {

    public void processorUpdated(ProcessorInfo ci);

    public void processorClosed(ProcessorInfo ci);
}
