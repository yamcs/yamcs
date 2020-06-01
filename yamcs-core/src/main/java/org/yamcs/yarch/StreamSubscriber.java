package org.yamcs.yarch;

@FunctionalInterface
public interface StreamSubscriber {

    void onTuple(Stream stream, Tuple tuple);

    default void streamClosed(Stream stream) {
    }
}
