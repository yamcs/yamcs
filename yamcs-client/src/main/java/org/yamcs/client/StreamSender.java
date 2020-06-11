package org.yamcs.client;

import java.util.concurrent.CompletableFuture;

public interface StreamSender<ItemT, ResponseT> {

    void send(ItemT message);

    CompletableFuture<ResponseT> complete();
}
