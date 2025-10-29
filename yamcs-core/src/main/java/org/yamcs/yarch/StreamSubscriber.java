package org.yamcs.yarch;

@FunctionalInterface
public interface StreamSubscriber {

    void onTuple(Stream stream, Tuple tuple);

    default void streamClosed(Stream stream) {
    }

    /**
     * This description (if not null) is returned in the API get/list stream(s) and shown also in the Yamcs Web
     */
    default String getDescription() {
        return null;
    }
}
