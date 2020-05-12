package org.yamcs.http;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

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

    private boolean replied;
    private List<Message> pendingMessages = new ArrayList<>(); // Messages received while not yet replied

    public WebSocketObserver(TopicContext ctx, NewWebSocketFrameHandler frameHandler) {
        this.ctx = ctx;
        this.frameHandler = frameHandler;
        log = new Log(WebSocketObserver.class);
        log.setContext(ctx.toString());

        log.info("Subscribe {}", ctx.getTopic().getName());
    }

    void sendReply(Reply reply) {
        synchronized (this) { // Guard 'replied' and 'pendingMessages'
            try {
                sendMessage("reply", reply);
            } finally {
                replied = true;
            }

            pendingMessages.forEach(message -> next(message));
            pendingMessages.clear();
        }
    }

    void sendOneof(String type, Message message) {
        sendMessage(type, message);
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
        // TODO this was originally designed for some calls (like parameter subscription)
        // which do not guarantee all messages to arrive (e.g. because the channel is
        // temporarily not writable). But this is perhaps a property that should be made
        // specific to the call. It may cause problems for other calls that require all
        // messages to arrive.
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
                log.info("Cancelling {} call ({})", ctx.getTopic().getName(), reason);
            } else {
                log.info("Cancelling {} call", ctx.getTopic().getName());
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
