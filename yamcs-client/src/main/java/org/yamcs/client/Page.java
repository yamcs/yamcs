package org.yamcs.client;

import java.util.concurrent.CompletableFuture;

public interface Page<T> extends Iterable<T> {

    boolean hasNextPage();

    CompletableFuture<Page<T>> getNextPage();
}
