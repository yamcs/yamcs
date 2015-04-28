package org.yamcs;

public interface YProcessorListener {

    void processorAdded(YProcessor channel);

    void yProcessorClosed(YProcessor channel);

    void processorStateChanged(YProcessor channel);
}
