package org.yamcs.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.YProcessorException;
import org.yamcs.archive.EventRecorder;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

/**
 * Provides realtime event subscription via web.
 */
public class EventResource extends AbstractWebSocketResource {
    private final Logger log;
    
    private Stream stream;
    private StreamSubscriber streamSubscriber;
    

    public EventResource(YProcessor channel, WebSocketServerHandler wsHandler) {
        super(channel, wsHandler);
        log = LoggerFactory.getLogger(EventResource.class.getName() + "[" + channel.getInstance() + "]");
        wsHandler.addResource("events", this);
        YarchDatabase ydb = YarchDatabase.getInstance(processor.getInstance());
        stream = ydb.getStream(EventRecorder.REALTIME_EVENT_STREAM_NAME);
    }

    @Override
    public WebSocketReplyData processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder, AuthenticationToken authenticationToken) throws WebSocketException {
        switch (ctx.getOperation()) {
        case "subscribe":
            return subscribe(ctx.getRequestId());
        case "unsubscribe":
            return unsubscribe(ctx.getRequestId());
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '"+ctx.getOperation()+"'");
        }
    }

    private WebSocketReplyData subscribe(int requestId) throws WebSocketException {
        doUnsubscribe(); // Only one subscription at a time
        doSubscribe();
        return toAckReply(requestId);
    }
    
    @Override
    public void switchYProcessor(YProcessor newProcessor, AuthenticationToken authToken) throws YProcessorException {
        doUnsubscribe();
        processor = newProcessor;
        YarchDatabase ydb = YarchDatabase.getInstance(processor.getInstance());
        stream = ydb.getStream(EventRecorder.REALTIME_EVENT_STREAM_NAME);
        doSubscribe();
    }
    
    private WebSocketReplyData unsubscribe(int requestId) throws WebSocketException {
        doUnsubscribe();
        return toAckReply(requestId);
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
                        wsHandler.sendData(ProtoDataType.EVENT, event, SchemaYamcs.Event.WRITE);
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
