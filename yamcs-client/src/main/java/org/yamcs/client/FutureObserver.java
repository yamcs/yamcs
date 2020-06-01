package org.yamcs.client;

import java.util.concurrent.CompletableFuture;

import org.yamcs.api.Observer;

public class FutureObserver<T> implements Observer<T> {

    private CompletableFuture<T> future;

    public FutureObserver(CompletableFuture<T> future) {
        this.future = future;
    }

    @Override
    public void next(T message) {
        future.complete(message);
    }

    @Override
    public void completeExceptionally(Throwable t) {
        future.completeExceptionally(t);
    }

    @Override
    public void complete() {
    }
}
