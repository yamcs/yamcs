package org.yamcs.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class EventResource extends AbstractWebSocketResource {

    private static final Logger log = LoggerFactory.getLogger(EventResource.class);
    public static final String RESOURCE_NAME = "events";

    public static final String OP_subscribe = "subscribe";
    public static final String OP_unsubscribe = "unsubscribe";

    private Stream stream;
    private StreamSubscriber streamSubscriber;

    public EventResource(WebSocketClient client) {
        super(client);
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(processor.getInstance());
        stream = ydb.getStream(EventRecorder.REALTIME_EVENT_STREAM_NAME);
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
    public void switchProcessor(Processor oldProcessor, Processor newProcessor) throws ProcessorException {
        if (streamSubscriber == null) {
            super.switchProcessor(oldProcessor, newProcessor);
        } else {
            doUnsubscribe();
            super.switchProcessor(oldProcessor, newProcessor);
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
    public void quit() {
        doUnsubscribe();
    }

    private void doSubscribe() {
        if (stream != null) {
            streamSubscriber = new StreamSubscriber() {
                @Override
                public void onTuple(Stream stream, Tuple tuple) {
                    try {
                        Event event = (Event) tuple.getColumn("body");
                        event = Event.newBuilder(event)
                                .setGenerationTimeUTC(TimeEncoding.toString(event.getGenerationTime()))
                                .setReceptionTimeUTC(TimeEncoding.toString(event.getReceptionTime()))
                                .build();
                        wsHandler.sendData(ProtoDataType.EVENT, event);
                    } catch (Exception e) {
                        log.warn("got error when sending event, quitting", e);
                        quit();
                    }
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
