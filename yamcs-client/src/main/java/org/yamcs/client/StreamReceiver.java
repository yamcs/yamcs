package org.yamcs.client;

@FunctionalInterface
public interface StreamReceiver<T> {

    void accept(T message);
}
