package org.yamcs.timeline;

public interface BatchObserver<T> {

    void complete(T summary);

    void completeExceptionally(Throwable t);
}
