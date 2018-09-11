package org.yamcs.web.websocket;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.protobuf.Archive.StreamData;
import org.yamcs.protobuf.Rest.StreamSubscribeRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.web.rest.archive.ArchiveHelper;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Capable of producing and consuming yarch Stream data (Tuples) over web socket
 */
public class StreamResource implements WebSocketResource {

    public static final String RESOURCE_NAME = "stream";

    private ConnectedWebSocketClient client;

    private List<Subscription> subscriptions = new ArrayList<>();

    private String yamcsInstance;

    public StreamResource(ConnectedWebSocketClient client) {
        this.client = client;
        Processor processor = client.getProcessor();
        if (processor != null) {
            yamcsInstance = processor.getInstance();
        }
    }

    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);

        // Optionally read body. If it's not provided, suppose the subscription concerns
        // the stream of the current processor (TODO currently doesn't work with JSON).
        Stream stream;
        StreamSubscribeRequest req = decoder.decodeMessageData(ctx, StreamSubscribeRequest.newBuilder()).build();
        if (req.hasStream()) {
            stream = ydb.getStream(req.getStream());
        } else {
            throw new WebSocketException(ctx.getRequestId(), "No stream was provided");
        }

        StreamSubscriber subscriber = new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                StreamData data = ArchiveHelper.toStreamData(stream, tuple);
                client.sendData(ProtoDataType.STREAM_DATA, data);
            }

            @Override
            public void streamClosed(Stream stream) {
            }
        };

        stream.addSubscriber(subscriber);
        subscriptions.add(new Subscription(stream, subscriber));
        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        return null;
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        yamcsInstance = processor.getInstance();
    }

    @Override
    public void unselectProcessor() {
        yamcsInstance = null;
    }

    @Override
    public void socketClosed() {
        for (Subscription subscription : subscriptions) {
            subscription.stream.removeSubscriber(subscription.subscriber);
        }
    }

    private static class Subscription {
        Stream stream;
        StreamSubscriber subscriber;

        Subscription(Stream stream, StreamSubscriber subscriber) {
            this.stream = stream;
            this.subscriber = subscriber;
        }
    }
}
