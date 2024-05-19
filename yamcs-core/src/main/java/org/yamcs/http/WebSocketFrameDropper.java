package org.yamcs.http;

import static org.yamcs.http.WebSocketFramePriority.HIGH;
import static org.yamcs.http.WebSocketFramePriority.LOW;
import static org.yamcs.http.WebSocketFramePriority.NORMAL;

import org.yamcs.logging.Log;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/**
 * Receives (potentially compressed) websocket frames (each containing a message), and handles message priorities, to
 * drop low priority messages, if they would otherwise exceed the high write watermark.
 */
public class WebSocketFrameDropper extends ChannelOutboundHandlerAdapter {

    private static final Log log = new Log(WebSocketFrameDropper.class);

    private final long highWaterMark;

    // avoid flooding the log with messsages about dropped frames
    private boolean logDroppedFrames = true;

    public WebSocketFrameDropper(long highWriteBufferWaterMark) {
        this.highWaterMark = highWriteBufferWaterMark;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof WebSocketFrame) {
            var frame = ((WebSocketFrame) msg);
            var prioAttr = ctx.channel().attr(WebSocketFramePriority.ATTR);
            var priority = prioAttr.get();
            if (priority == null) { // Could be a ping frame
                priority = NORMAL;
            }

            Channel nettyChannel = ctx.channel();
            long frameLength = frame.content().readableBytes();
            long bytesBeforeUnwritable = nettyChannel.bytesBeforeUnwritable();

            boolean send = priority == HIGH ||
                    (priority == NORMAL && bytesBeforeUnwritable > 0) ||
                    (priority == LOW && bytesBeforeUnwritable > frameLength);

            if (send) {
                ctx.write(frame, promise);
                logDroppedFrames = true;
            } else {
                if (priority == LOW) {
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
        } else {
            super.write(ctx, msg, promise);
        }
    }

    @SuppressWarnings("serial")
    public static class MessageDroppedException extends Exception {
        final long bytesBeforeUnwritable;

        public MessageDroppedException(long bytesBeforeUnwritable) {
            this.bytesBeforeUnwritable = bytesBeforeUnwritable;
        }
    }
}
