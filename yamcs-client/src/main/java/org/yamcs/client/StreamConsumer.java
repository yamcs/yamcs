package org.yamcs.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.yamcs.api.Observer;

// TODO add cancellation
public abstract class StreamConsumer<T> implements Observer<T> {

    private CompletableFuture<Void> future = new CompletableFuture<>();

    @Override
    public abstract void next(T message);

    @Override
    public void completeExceptionally(Throwable t) {
        future.completeExceptionally(t);
    }

    @Override
    public void complete() {
        future.complete(null);
    }

    public boolean isDone() {
        return future.isDone();
    }

    public void awaitDone() throws InterruptedException, ExecutionException {
        future.get();
    }

    public void awaitDone(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        future.get(timeout, unit);
    }
}
