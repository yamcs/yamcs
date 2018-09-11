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
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        doUnsubscribe(); // Only one subscription at a time
        doSubscribe();
        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        doUnsubscribe();
        return WebSocketReply.ack(ctx.getRequestId());
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
