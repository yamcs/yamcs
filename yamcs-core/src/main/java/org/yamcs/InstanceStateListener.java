package org.yamcs;

public interface InstanceStateListener {

    default void initializing() {
    }

    default void initialized() {
    }

    default void starting() {
    }

    default void running() {
    }

    default void stopping() {
    }

    default void offline() {
    }

    default void failed(Throwable failure) {
    }
}
