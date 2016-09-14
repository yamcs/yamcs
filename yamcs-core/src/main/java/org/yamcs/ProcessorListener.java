package org.yamcs;

public interface ProcessorListener {

    void processorAdded(YProcessor processor);

    void processorClosed(YProcessor processor);

    void processorStateChanged(YProcessor processor);
}
