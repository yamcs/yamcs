package org.yamcs.http;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.yamcs.api.Observer;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.Reply;
import org.yamcs.protobuf.ServerMessage;

import com.google.protobuf.Any;
import com.google.protobuf.Message;

public class WebSocketObserver implements Observer<Message> {

    private Log log;

    private TopicContext ctx;
    private NewWebSocketFrameHandler frameHandler;

    private int messageCount = 0;
    private boolean cancelled;
    private boolean completed;
    private Runnable cancelHandler;

    private CountDownLatch replyTriggered = new CountDownLatch(1);

    public WebSocketObserver(TopicContext ctx, NewWebSocketFrameHandler frameHandler) {
        this.ctx = ctx;
        this.frameHandler = frameHandler;
        log = new Log(WebSocketObserver.class);
        log.setContext(ctx.toString());

        log.info("Subscribe {}", ctx.getTopic().getName());
    }

    void sendReply(Reply reply) {
        try {
            sendMessage("reply", reply);
        } finally {
            replyTriggered.countDown();
        }
    }

    void sendOneof(String type, Message message) {
        sendMessage(type, message);
    }

    @Override
    public void next(Message message) {

        // Block until the reply was sent. We really want the client to receive data only
        // *after* the initial call reply.
        try {
            replyTriggered.await(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            cancelCall(e.getMessage());
            throw new IllegalStateException("No reply sent in under 5 seconds");
        }

        // While we were wating, the call may have been cancelled for some reason.
        if (cancelled) {
            return;
        }

        messageCount++;

        if (!ctx.nettyContext.channel().isOpen()) {
            log.warn("Skipping frame because channel is not open");
            return;
        }
        if (!ctx.nettyContext.channel().isWritable()) {
            log.warn("Skipping frame because channel is not writable");
            return;
        }

        sendMessage(ctx.getTopic().getName(), message);
    }

    private void sendMessage(String type, Message data) {
        ServerMessage message = ServerMessage.newBuilder()
                .setType(type)
                .setCall(ctx.getId())
                .setSeq(messageCount)
                .setData(Any.pack(data, HttpServer.TYPE_URL_PREFIX))
                .build();
        try {
            frameHandler.writeMessage(ctx.nettyContext, message);
        } catch (IOException e) {
            cancelCall(e.getMessage());
            throw new UncheckedIOException(e);
        }
    }

    void cancelCall(String reason) {
        if (!cancelled) {
            if (reason != null) {
                log.info("Cancelling call ({})", reason);
            } else {
                log.info("Cancelling call");
            }
            cancelled = true;
            if (cancelHandler != null) {
                cancelHandler.run();
            }
        }
    }

    @Override
    public void completeExceptionally(Throwable t) {
        if (completed) {
            throw new IllegalStateException("Observer already completed");
        }
        completed = true;
    }

    @Override
    public void complete() {
        if (completed) {
            throw new IllegalStateException("Observer already completed");
        }
        completed = true;

        if (cancelled) {
            ctx.requestFuture.complete(null);
            return;
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelHandler(Runnable cancelHandler) {
        this.cancelHandler = cancelHandler;
    }
}
