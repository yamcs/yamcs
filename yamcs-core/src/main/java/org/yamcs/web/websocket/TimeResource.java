package org.yamcs.web.websocket;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.protobuf.Web.TimeSubscriptionResponse;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.TimeInfo;
import org.yamcs.utils.TimeEncoding;

public class TimeResource extends AbstractWebSocketResource {

    public static final String RESOURCE_NAME = "time";
    public static final String OP_subscribe = "subscribe";
    public static final String OP_unsubscribe = "unsubscribe";
    private static ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);

    private Processor processor;

    private AtomicBoolean subscribed = new AtomicBoolean(false);

    private ScheduledFuture<?> future = null;

    public TimeResource(ConnectedWebSocketClient client) {
        super(client);
        processor = client.getProcessor();
    }

    @Override
    public WebSocketReply processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        switch (ctx.getOperation()) {
        case OP_subscribe:
            return processSubscribeRequest(ctx, decoder);
        case OP_unsubscribe:
            if (future != null) {
                future.cancel(false);
            }
            subscribed.set(false);
            return WebSocketReply.ack(ctx.getRequestId());
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
    }

    private WebSocketReply processSubscribeRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        if (!subscribed.getAndSet(true)) {
            future = timer.scheduleAtFixedRate(() -> {
                long currentTime = processor.getCurrentTime();
                TimeInfo ti = TimeInfo.newBuilder()
                        .setCurrentTime(currentTime)
                        .setCurrentTimeUTC(TimeEncoding.toString(currentTime))
                        .build();
                wsHandler.sendData(ProtoDataType.TIME_INFO, ti);
            }, 1, 1, TimeUnit.SECONDS);
        }

        WebSocketReply reply = new WebSocketReply(ctx.getRequestId());

        // Already send actual time in response, for client convenience.
        long currentTime = processor.getCurrentTime();
        TimeInfo ti = TimeInfo.newBuilder()
                .setCurrentTime(currentTime)
                .setCurrentTimeUTC(TimeEncoding.toString(currentTime))
                .build();
        TimeSubscriptionResponse response = TimeSubscriptionResponse.newBuilder()
                .setTimeInfo(ti)
                .build();
        reply.attachData(TimeResource.class.getSimpleName(), response);

        wsHandler.sendReply(reply);
        return null;
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        this.processor = processor;
    }

    @Override
    public void unselectProcessor() {
        processor = null;
    }

    @Override
    public void socketClosed() {
        if (future != null) {
            future.cancel(false);
        }
    }
}
