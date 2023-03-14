package org.yamcs.client.base;

import java.util.concurrent.CompletableFuture;

import org.yamcs.api.Observer;

/**
 * An observer that completes a future based on a single response.
 */
public class ResponseObserver<T> implements Observer<T> {

    private CompletableFuture<T> future;

    public ResponseObserver(CompletableFuture<T> future) {
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
        if (!future.isDone()) {
            future.completeExceptionally(new IllegalStateException("no response received"));
        }
    }
}
