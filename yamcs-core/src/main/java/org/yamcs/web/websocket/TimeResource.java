package org.yamcs.web.websocket;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Web.TimeSubscriptionResponse;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.TimeInfo;
import org.yamcs.utils.TimeEncoding;

public class TimeResource extends AbstractWebSocketResource {

    private static final Logger log = LoggerFactory.getLogger(TimeResource.class);
    public static final String RESOURCE_NAME = "time";
    public static final String OP_subscribe = "subscribe";
    public static final String OP_unsubscribe = "unsubscribe";
    private static ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);

    private AtomicBoolean subscribed = new AtomicBoolean(false);

    private ScheduledFuture<?> future = null;

    public TimeResource(WebSocketClient client) {
        super(client);
    }

    @Override
    public WebSocketReply processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        switch (ctx.getOperation()) {
        case OP_subscribe:
            return processSubscribeRequest(ctx, decoder);
        case OP_unsubscribe:
            doUnsubscribe();
            return WebSocketReply.ack(ctx.getRequestId());
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
    }

    private WebSocketReply processSubscribeRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        if (!subscribed.getAndSet(true)) {
            future = timer.scheduleAtFixedRate(() -> {
                try {
                    long currentTime = processor.getCurrentTime();
                    TimeInfo ti = TimeInfo.newBuilder()
                            .setCurrentTime(currentTime)
                            .setCurrentTimeUTC(TimeEncoding.toString(currentTime))
                            .build();
                    wsHandler.sendData(ProtoDataType.TIME_INFO, ti);
                } catch (IOException e) {
                    log.debug("Could not send time info data", e);
                }
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

        try {
            wsHandler.sendReply(reply);
        } catch (IOException e) {
            log.error("Exception while sending reply", e);
        }
        return null;
    }

    private void doUnsubscribe() {
        if (future != null) {
            future.cancel(false);
        }
        subscribed.set(false);
    }

    @Override
    public void quit() {
        doUnsubscribe();
    }
}
