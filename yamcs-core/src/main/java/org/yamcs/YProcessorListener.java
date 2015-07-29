package org.yamcs;

public interface YProcessorListener {

    void processorAdded(YProcessor processor);

    void yProcessorClosed(YProcessor processor);

    void processorStateChanged(YProcessor processor);
}
