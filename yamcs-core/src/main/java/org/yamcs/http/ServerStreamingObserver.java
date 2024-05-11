package org.yamcs.http;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_DISPOSITION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderValues.CHUNKED;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.yamcs.NotThreadSafe;
import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.logging.Log;

import com.google.protobuf.Message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;

/**
 * A message observer that implements a streaming response over HTTP using chunked transfer encoding.
 */
@NotThreadSafe
public class ServerStreamingObserver implements Observer<Message> {

    private static final int CHUNK_SIZE = 8096;
    private static final Log log = new Log(ServerStreamingObserver.class);

    private RouteContext ctx;

    private MediaType mediaType;

    private ByteBuf buf;
    protected ByteBufOutputStream bufOut;

    private int messageCount = 0;
    private boolean cancelled;
    private boolean completed;
    private Runnable cancelHandler;

    public ServerStreamingObserver(RouteContext ctx) {
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
                    String json = ctx.printJson(message);
                    bufOut.write(json.getBytes(StandardCharsets.UTF_8));
                }
            }

            if (buf.readableBytes() >= CHUNK_SIZE) {
                bufOut.close();
                ctx.addTransferredSize(buf.readableBytes());
                writeChunk(buf);
                resetBuffer();
            }
        } catch (ClosedChannelException e) {
            cancelCall("closed channel");
            // No rethrow. Client disconnect is a normal condition
        } catch (IOException e) {
            cancelCall(e.toString());
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
            mediaType = RouteContext.deriveTargetContentType(ctx.nettyRequest);
        }

        startChunkedTransfer(mediaType, filename);
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

        if (messageCount == 0) {
            initializeHttpResponse(null);
        } else {
            try {
                bufOut.close();
                if (buf.readableBytes() > 0) {
                    ctx.addTransferredSize(buf.readableBytes());
                    writeChunk(buf);
                }
            } catch (IOException e) {
                log.error("Could not write final chunk of data", e);
            }
        }

        ctx.nettyContext.channel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
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

    private void startChunkedTransfer(MediaType contentType, String filename) {
        log.info("{}: {} {} 200 starting chunked transfer", ctx, ctx.nettyRequest.method(), ctx.nettyRequest.uri());
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(TRANSFER_ENCODING, CHUNKED);
        response.headers().set(CONTENT_TYPE, contentType);

        // Set Content-Disposition header so that supporting clients will treat
        // response as a downloadable file
        if (filename != null) {
            response.headers().set(CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        }
        ctx.nettyContext.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    private void writeChunk(ByteBuf buf) throws IOException {
        Channel ch = ctx.nettyContext.channel();
        if (!ch.isOpen()) {
            throw new ClosedChannelException();
        }
        ChannelFuture writeFuture = ctx.nettyContext.channel().writeAndFlush(new DefaultHttpContent(buf));
        try {
            if (!ch.isWritable()) {
                boolean writeCompleted = writeFuture.await(10, TimeUnit.SECONDS);
                if (!writeCompleted) {
                    throw new IOException("Channel did not become writable in 10 seconds");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
