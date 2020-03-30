package org.yamcs.http;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.yamcs.NotThreadSafe;
import org.yamcs.api.ExceptionMessage;
import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.logging.Log;
import org.yamcs.utils.ExceptionUtil;

import com.google.protobuf.Empty;
import com.google.protobuf.Message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Observes the state of a single RPC call where both request and response are non-streaming.
 */
@NotThreadSafe
public class CallObserver implements Observer<Message> {

    private static final Log log = new Log(CallObserver.class);

    private RouteContext ctx;

    private boolean completed;

    public CallObserver(RouteContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void next(Message message) {
        if (message instanceof Empty) {
            HttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK);
            httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
            completeRequest(httpResponse);
        } else if (message instanceof HttpBody) {
            HttpBody responseBody = (HttpBody) message;
            ByteBuf buf = Unpooled.wrappedBuffer(responseBody.getData().toByteArray());
            HttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, buf);
            httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, responseBody.getContentType());
            httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
            ctx.addTransferredSize(buf.readableBytes());
            completeRequest(httpResponse);
        } else {
            sendMessageResponse(message).addListener(l -> {
                ctx.requestFuture.complete(null);
            });
        }
    }

    @Override
    public void completeExceptionally(Throwable t) {
        if (completed) {
            throw new IllegalStateException("Observer already completed");
        }
        completed = true;

        t = ExceptionUtil.unwind(t);
        HttpException httpException;
        if (t instanceof HttpException) {
            httpException = (HttpException) t;
        } else {
            httpException = new InternalServerErrorException(t);
        }

        ChannelFuture cf = sendError(ctx, httpException);
        cf.addListener(l -> {
            ctx.requestFuture.completeExceptionally(httpException);
            if (!l.isSuccess()) {
                log.error("Network error", l.cause());
            }
        });
    }

    @Override
    public void complete() {
        if (completed) {
            throw new IllegalStateException("Observer already completed");
        }
        completed = true;
    }

    private void completeRequest(HttpResponse httpResponse) {
        ChannelFuture cf = HttpRequestHandler.sendResponse(ctx.nettyContext,
                ctx.nettyRequest, httpResponse);
        ctx.reportStatusCode(httpResponse.status().code());
        cf.addListener(l -> {
            ctx.requestFuture.complete(null);
            if (!l.isSuccess()) {
                log.error("Network error", l.cause());
            }
        });
    }

    private <T extends Message> ChannelFuture sendMessageResponse(T responseMsg) {
        HttpRequest req = ctx.nettyRequest;

        ByteBuf body = ctx.nettyContext.alloc().buffer();
        MediaType contentType = ctx.deriveTargetContentType();
        if (contentType != MediaType.JSON) {
            ctx.reportStatusCode(OK.code());
            return HttpRequestHandler.sendMessageResponse(ctx.nettyContext, req, OK, responseMsg);
        } else {
            try (ByteBufOutputStream channelOut = new ByteBufOutputStream(body)) {
                contentType = MediaType.JSON;
                String str = ctx.printJson(responseMsg);
                body.writeCharSequence(str, StandardCharsets.UTF_8);
            } catch (IOException e) {
                HttpResponseStatus status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                ctx.reportStatusCode(status.code());
                return HttpRequestHandler.sendPlainTextError(ctx.nettyContext, req, status, e.toString());
            }
            HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, body);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType.toString());
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes());
            ctx.reportStatusCode(OK.code());
            return HttpRequestHandler.sendResponse(ctx.nettyContext, req, response);
        }
    }

    static ChannelFuture sendError(RouteContext ctx, HttpException t) {
        ExceptionMessage msg = t.toMessage();
        ctx.reportStatusCode(t.getStatus().code());
        return HttpRequestHandler.sendMessageResponse(ctx.nettyContext, ctx.nettyRequest, t.getStatus(), msg);
    }
}
