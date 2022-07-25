package org.yamcs.http;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.api.Observer;
import org.yamcs.http.WebSocketFrameHandler.Priority;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.Reply;
import org.yamcs.protobuf.ServerMessage;

import com.google.protobuf.Any;
import com.google.protobuf.Message;

public class WebSocketObserver implements Observer<Message> {

    private Log log;

    private final TopicContext ctx;
    private final WebSocketFrameHandler frameHandler;

    private int messageCount = 0;
    private int dropCount = 0;
    private final int maxDrops;
    private final boolean lowPriority;
    private boolean cancelled;
    private boolean completed;
    private Runnable cancelHandler;

    private boolean replied;
    private List<Message> pendingMessages = new ArrayList<>(); // Messages received while not yet replied

    public WebSocketObserver(TopicContext ctx, WebSocketFrameHandler frameHandler) {
        this.ctx = ctx;
        this.frameHandler = frameHandler;
        this.maxDrops = ctx.getMaxDroppedWrites();
        this.lowPriority = ctx.isLowPriority();
        log = new Log(WebSocketObserver.class);
        log.setContext(ctx.toString());

        log.debug("Subscribe {}", ctx.getTopic().getName());
    }

    void sendReply(Reply reply) {
        synchronized (this) { // Guard 'replied' and 'pendingMessages'
            try {
                sendMessage("reply", reply, Priority.HIGH);
            } finally {
                replied = true;
            }

            pendingMessages.forEach(message -> next(message));
            pendingMessages.clear();
        }
    }

    @Override
    public void next(Message message) {
        synchronized (this) {
            if (!replied) {
                pendingMessages.add(message);
                return;
            }
        }

        // Increase even if it not sent.
        messageCount++;

        if (!ctx.nettyContext.channel().isOpen()) {
            ctx.cancel(null);
            ctx.nettyContext.close();
            return;
        }

        boolean sent = sendMessage(ctx.getTopic().getName(), message, lowPriority ? Priority.LOW : Priority.NORMAL);
        if (!sent) {
            dropCount++;

            if (!lowPriority && dropCount >= maxDrops) {
                log.warn("Too many ({}) dropped messages. Forcing disconnect", dropCount);
                ctx.cancel(null); // Cancel the call first, to avoid log messages going beyond maxDrops
                ctx.nettyContext.close();
            }
            return;
        }
        dropCount = 0;
    }

    private boolean sendMessage(String type, Message data, Priority priority) {
        ServerMessage message = ServerMessage.newBuilder()
                .setType(type)
                .setCall(ctx.getId())
                .setSeq(messageCount)
                .setData(Any.pack(data, HttpServer.TYPE_URL_PREFIX))
                .build();
        try {
            return frameHandler.writeMessage(ctx.nettyContext, message, priority);
        } catch (IOException e) {
            cancelCall(e.getMessage());
            throw new UncheckedIOException(e);
        }
    }

    void cancelCall(String reason) {
        if (!cancelled) {
            if (reason != null) {
                log.debug("Cancelling {} call ({})", ctx.getTopic().getName(), reason);
            } else {
                log.debug("Cancelling {} call", ctx.getTopic().getName());
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
