package org.yamcs.http.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;

import org.yamcs.NotThreadSafe;
import org.yamcs.api.HttpBody;
import org.yamcs.api.MediaType;
import org.yamcs.api.Observer;
import org.yamcs.http.HttpRequestHandler;
import org.yamcs.logging.Log;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.LastHttpContent;

/**
 * A message observer that implements a streaming response over HTTP using chunked transfer encoding.
 */
@NotThreadSafe
public class ServerStreamingObserver implements Observer<Message> {

    private static final int CHUNK_SIZE = 8096;
    private static final Log log = new Log(ServerStreamingObserver.class);

    private Context ctx;

    private MediaType mediaType;

    private ByteBuf buf;
    protected ByteBufOutputStream bufOut;

    private int messageCount = 0;
    private boolean cancelled;
    private boolean completed;
    private Runnable cancelHandler;

    public ServerStreamingObserver(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public void next(Message message) {
        if (completed) {
            throw new IllegalStateException("Observer already completed");
        }

        if (messageCount == 0) {
            initializeHttpResponse(message);
        }

        try {
            if (message instanceof HttpBody) {
                HttpBody body = (HttpBody) message;
                if (body.hasData()) {
                    body.getData().writeTo(bufOut);
                }
            } else {
                if (MediaType.PROTOBUF.equals(mediaType)) {
                    message.writeDelimitedTo(bufOut);
                } else {
                    String json = JsonFormat.printer().print(message);
                    bufOut.write(json.getBytes(StandardCharsets.UTF_8));
                }
            }

            if (buf.readableBytes() >= CHUNK_SIZE) {
                bufOut.close();
                ctx.addTransferredSize(buf.readableBytes());
                HttpRequestHandler.writeChunk(ctx.nettyContext, buf);
                resetBuffer();
            }
        } catch (ClosedChannelException e) {
            cancelCall("closed channel");
            // No rethrow. Client disconnect is a normal condition
        } catch (IOException e) {
            cancelCall(e.getMessage());
            throw new UncheckedIOException(e);
        }

        messageCount++;
    }

    private void cancelCall(String reason) {
        if (!cancelled) {
            log.info("Cancelling call ({})", reason);
            cancelled = true;
            if (cancelHandler != null) {
                cancelHandler.run();
            }
        }
    }

    private void initializeHttpResponse(Message firstMessage) {
        resetBuffer();

        String filename = null;

        if (firstMessage instanceof HttpBody) {
            HttpBody body = (HttpBody) firstMessage;
            mediaType = MediaType.from(body.getContentType());
            if (body.hasFilename()) {
                filename = body.getFilename();
            }
        } else {
            mediaType = RestRequest.deriveTargetContentType(ctx.nettyRequest);
        }

        HttpRequestHandler.startChunkedTransfer(
                ctx.nettyContext, ctx.nettyRequest, mediaType, filename);
        ctx.reportStatusCode(200);
    }

    private void resetBuffer() {
        buf = ctx.nettyContext.alloc().buffer();
        bufOut = new ByteBufOutputStream(buf);
    }

    @Override
    public void completeExceptionally(Throwable t) {
        if (completed) {
            throw new IllegalStateException("Observer already completed");
        }
        completed = true;

        Channel ch = ctx.nettyContext.channel();
        if (ch.isOpen()) {
            log.warn("Closing channel because transfer failed");
            ch.close();
        }
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

        try {
            bufOut.close();
            if (buf.readableBytes() > 0) {
                ctx.addTransferredSize(buf.readableBytes());
                HttpRequestHandler.writeChunk(ctx.nettyContext, buf);
            }
        } catch (IOException e) {
            log.error("Could not write final chunk of data", e);
        }

        ctx.nettyContext.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                .addListener(ChannelFutureListener.CLOSE)
                .addListener(l -> {
                    if (l.isSuccess()) {
                        ctx.requestFuture.complete(null);
                    } else {
                        ctx.requestFuture.completeExceptionally(l.cause());
                    }
                });
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
