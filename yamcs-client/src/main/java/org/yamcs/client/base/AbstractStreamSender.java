package org.yamcs.client.base;

import java.util.concurrent.CompletableFuture;

import org.yamcs.client.ClientException;
import org.yamcs.client.StreamSender;

import com.google.protobuf.Message;

public class AbstractStreamSender<ItemT extends Message, ResponseT> implements StreamSender<ItemT, ResponseT> {

    private BulkRestDataSender baseSender;

    public AbstractStreamSender(BulkRestDataSender baseSender) {
        this.baseSender = baseSender;
    }

    @Override
    public void send(ItemT message) {
        try {
            baseSender.sendData(message.toByteArray());
        } catch (ClientException e) {
            // TODO somehow emit to general future
            e.printStackTrace();
        }
    }

    @Override
    public CompletableFuture<ResponseT> complete() {
        return null;
    }
}
