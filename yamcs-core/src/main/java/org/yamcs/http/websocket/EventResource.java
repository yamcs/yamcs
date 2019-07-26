package org.yamcs.http.websocket;

import java.util.concurrent.atomic.AtomicBoolean;

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

    private ConnectedWebSocketClient client;

    private Processor processor;

    private Stream stream;
    private StreamSubscriber streamSubscriber;

    private AtomicBoolean subscribed = new AtomicBoolean(false);

    public EventResource(ConnectedWebSocketClient client) {
        this.client = client;
        processor = client.getProcessor();
    }

    @Override
    public String getName() {
        return "events";
    }

    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        if (!subscribed.getAndSet(true)) {
            doSubscribe();
        }
        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        doUnsubscribe();
        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public void unselectProcessor() {
        processor = null;
        doUnsubscribe();
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        this.processor = processor;
        if (subscribed.get()) {
            doSubscribe();
        }
    }

    private void doSubscribe() {
        if (processor == null) {
            return;
        }

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(processor.getInstance());
        stream = ydb.getStream(EventRecorder.REALTIME_EVENT_STREAM_NAME);
        if (stream == null) {
            return;
        }

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

    private void doUnsubscribe() {
        if (streamSubscriber != null) {
            stream.removeSubscriber(streamSubscriber);
        }
        streamSubscriber = null;
        stream = null;
        subscribed.set(false);
    }

    @Override
    public void socketClosed() {
        doUnsubscribe();
    }
}
