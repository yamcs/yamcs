package org.yamcs.http;

import org.yamcs.logging.Log;
import org.yamcs.protobuf.ServerMessage;

import com.google.protobuf.Any;
import com.google.protobuf.Message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/**
 * Encodes {@link InternalServerMessage} to either {@link BinaryWebSocketFrame} or {@link TextWebSocketFrame} depending
 * if the protobuf or json has to be sent.
 * <p>
 * It takes care of the message priorities, dropping messages with low priority if they would exceed the high water mark
 *
 */
public class WebSocketServerMessageHandler extends ChannelOutboundHandlerAdapter {
    public static enum Priority {
        /* messages are dropped if causing the channel to become not writable */
        LOW,
        /* messages are dropped if the channel is not writable */
        NORMAL,
        /*
         * messages are written (in fact queued by netty) even if the channel is not writable.
         * Too many of these will cause OOM
         */
        HIGH
    };

    private static final Log log = new Log(WebSocketServerMessageHandler.class);

    final boolean protobuf;
    final HttpServer httpServer;
    final long highWaterMark;

    // avoid flooding the log with messsages about dropped frames
    boolean logDroppedFrames = true;

    public WebSocketServerMessageHandler(HttpServer httpServer, boolean protobuf, long highWriteBufferWaterMark) {
        this.httpServer = httpServer;
        this.protobuf = protobuf;
        this.highWaterMark = highWriteBufferWaterMark;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        InternalServerMessage imsg = (InternalServerMessage) msg;
        ServerMessage serverMessage = imsg.toProtobuf();
        Priority priority = imsg.priority;
        WebSocketFrame frame;

        if (protobuf) {
            ByteBuf buf = ctx.alloc().buffer();
            try (ByteBufOutputStream bufOut = new ByteBufOutputStream(buf)) {
                serverMessage.writeTo(bufOut);
            }
            frame = new BinaryWebSocketFrame(buf);
        } else {
            String json = httpServer.getJsonPrinter().print(serverMessage);
            frame = new TextWebSocketFrame(json);
        }
        Channel nettyChannel = ctx.channel();
        long frameLength = frame.content().readableBytes();
        long bytesBeforeUnwritable = nettyChannel.bytesBeforeUnwritable();

        boolean send = priority == Priority.HIGH ||
                (priority == Priority.NORMAL && bytesBeforeUnwritable > 0) ||
                (priority == Priority.LOW && bytesBeforeUnwritable > frameLength);

        if (send) {
            ctx.write(frame, promise);
            logDroppedFrames = true;
        } else {
            if (priority == Priority.LOW) {
                if (logDroppedFrames) {
                    log.warn("Frame skipped because writing the frame would make the channel not writable "
                            + "(frameLength: {}, bytesBeforeUnwritable: {})", frameLength, bytesBeforeUnwritable);
                    if (frameLength > highWaterMark) {
                        log.warn("This frame size exceeds the high water mark (currently set to {}) "
                                + "so it will always be dropped. Consider increasing the high water mark",
                                highWaterMark);
                    }
                    logDroppedFrames = false;
                }
            } else {
                log.warn("Channel full, cannot write message with priority=" + priority
                        + " (slow network?). Closing connection.");
                ctx.close();
            }
            promise.setFailure(new MessageDroppedException(bytesBeforeUnwritable));
        }
    }

    @SuppressWarnings("serial")
    public static class MessageDroppedException extends Exception {
        final long bytesBeforeUnwritable;

        public MessageDroppedException(long bytesBeforeUnwritable) {
            this.bytesBeforeUnwritable = bytesBeforeUnwritable;
        }
    }

    public static class InternalServerMessage {
        final String type;

        final int call;
        final int seq;
        final Message data;
        final Priority priority;

        public InternalServerMessage(String type, int call, int seq, Message data, Priority priority) {
            this.type = type;
            this.call = call;
            this.seq = seq;
            this.data = data;
            this.priority = priority;
        }

        public InternalServerMessage(String type, Message data) {
            this(type, 0, 0, data, Priority.HIGH);
        }

        public ServerMessage toProtobuf() {
            return ServerMessage.newBuilder().setType(type).setCall(call).setSeq(seq)
                    .setData(Any.pack(data, HttpServer.TYPE_URL_PREFIX))
                    .build();
        }
    }
}
