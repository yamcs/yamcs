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

public class TimeResource implements WebSocketResource {

    public static final String RESOURCE_NAME = "time";
    private static ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);

    private ConnectedWebSocketClient client;

    private Processor processor;

    private AtomicBoolean subscribed = new AtomicBoolean(false);

    private ScheduledFuture<?> future = null;

    public TimeResource(ConnectedWebSocketClient client) {
        this.client = client;
        processor = client.getProcessor();
    }

    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        if (!subscribed.getAndSet(true)) {
            future = timer.scheduleAtFixedRate(() -> {
                if (processor != null && processor.isRunning()) {
                    long currentTime = processor.getCurrentTime();
                    client.sendData(ProtoDataType.TIME_INFO, TimeInfo.newBuilder()
                            .setCurrentTime(currentTime)
                            .setCurrentTimeUTC(TimeEncoding.toString(currentTime))
                            .build());
                }
            }, 1, 1, TimeUnit.SECONDS);
        }

        WebSocketReply reply = new WebSocketReply(ctx.getRequestId());

        TimeSubscriptionResponse.Builder responseb = TimeSubscriptionResponse.newBuilder();

        // Already send actual time in response, for client convenience.
        if (processor != null && processor.isRunning()) {
            long currentTime = processor.getCurrentTime();
            responseb.setTimeInfo(TimeInfo.newBuilder()
                    .setCurrentTime(currentTime)
                    .setCurrentTimeUTC(TimeEncoding.toString(currentTime)));
        }
        reply.attachData(TimeResource.class.getSimpleName(), responseb.build());

        client.sendReply(reply);
        return null;
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        if (future != null) {
            future.cancel(false);
        }
        subscribed.set(false);
        return WebSocketReply.ack(ctx.getRequestId());
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
