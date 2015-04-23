package org.yamcs;

public interface YProcessorListener {

    void yProcessorAdded(YProcessor channel);

    void yProcessorClosed(YProcessor channel);

    void yProcessorStateChanged(YProcessor channel);
}
