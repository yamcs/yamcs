package org.yamcs.web.websocket;

import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.archive.EventRecorder;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Provides realtime event subscription via web.
 */
public class EventResource implements WebSocketResource {

    public static final String RESOURCE_NAME = "events";

    public static final String OP_subscribe = "subscribe";
    public static final String OP_unsubscribe = "unsubscribe";

    private ConnectedWebSocketClient client;

    private Stream stream;
    private StreamSubscriber streamSubscriber;

    public EventResource(ConnectedWebSocketClient client) {
        this.client = client;
        Processor processor = client.getProcessor();
        if (processor != null) {
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(processor.getInstance());
            stream = ydb.getStream(EventRecorder.REALTIME_EVENT_STREAM_NAME);
        }
    }

    @Override
    public WebSocketReply processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        switch (ctx.getOperation()) {
        case OP_subscribe:
            return subscribe(ctx.getRequestId());
        case OP_unsubscribe:
            return unsubscribe(ctx.getRequestId());
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
    }

    private WebSocketReply subscribe(int requestId) throws WebSocketException {
        doUnsubscribe(); // Only one subscription at a time
        doSubscribe();
        return WebSocketReply.ack(requestId);
    }

    @Override
    public void unselectProcessor() {
        doUnsubscribe();
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        if (streamSubscriber != null) {
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(processor.getInstance());
            stream = ydb.getStream(EventRecorder.REALTIME_EVENT_STREAM_NAME);
            doSubscribe();
        }
    }

    private WebSocketReply unsubscribe(int requestId) throws WebSocketException {
        doUnsubscribe();
        return WebSocketReply.ack(requestId);
    }

    @Override
    public void socketClosed() {
        doUnsubscribe();
    }

    private void doSubscribe() {
        if (stream != null) {
            streamSubscriber = new StreamSubscriber() {
                @Override
                public void onTuple(Stream stream, Tuple tuple) {
                    Event event = (Event) tuple.getColumn("body");
                    event = Event.newBuilder(event)
                            .setGenerationTimeUTC(TimeEncoding.toString(event.getGenerationTime()))
                            .setReceptionTimeUTC(TimeEncoding.toString(event.getReceptionTime()))
                            .build();
                    client.sendData(ProtoDataType.EVENT, event);
                }

                @Override
                public void streamClosed(Stream stream) {
                }
            };
            stream.addSubscriber(streamSubscriber);
        }
    }

    private void doUnsubscribe() {
        if (streamSubscriber != null) {
            stream.removeSubscriber(streamSubscriber);
        }
        streamSubscriber = null;
    }
}
