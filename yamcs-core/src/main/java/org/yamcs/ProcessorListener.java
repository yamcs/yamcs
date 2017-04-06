package org.yamcs;

public interface ProcessorListener {

    void processorAdded(Processor processor);

    void processorClosed(Processor processor);

    void processorStateChanged(Processor processor);
}
