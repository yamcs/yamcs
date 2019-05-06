package org.yamcs.yarch;

public interface StreamSubscriber {
    void onTuple(Stream stream, Tuple tuple);

    default void streamClosed(Stream stream) {};
}
