package org.yamcs.http;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
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

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Empty;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import com.google.protobuf.util.FieldMaskUtil;

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
            if (responseBody.hasFilename()) {
                httpResponse.headers().set(HttpHeaderNames.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + responseBody.getFilename() + "\"");
            }
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

    @SuppressWarnings("unchecked")
    private <T extends Message> ChannelFuture sendMessageResponse(T responseMsg) {
        HttpRequest req = ctx.nettyRequest;

        if (ctx.fieldMask != null) {
            if (ctx.getFieldMaskRoot() == null) {
                Builder builder = responseMsg.newBuilderForType();
                FieldMaskUtil.merge(ctx.fieldMask, responseMsg, builder);
                responseMsg = (T) builder.buildPartial();
            } else {
                FieldDescriptor maskRoot = responseMsg.getDescriptorForType()
                        .findFieldByName(ctx.getFieldMaskRoot());
                if (maskRoot != null) {
                    Builder builder = responseMsg.toBuilder();
                    builder.clearField(maskRoot);
                    if (maskRoot.isRepeated()) {
                        int n = responseMsg.getRepeatedFieldCount(maskRoot);
                        for (int i = 0; i < n; i++) {
                            Message repeatedMessage = (Message) responseMsg.getRepeatedField(maskRoot, i);
                            Builder repeatedBuilder = repeatedMessage.newBuilderForType();
                            FieldMaskUtil.merge(ctx.fieldMask, repeatedMessage, repeatedBuilder);
                            builder.addRepeatedField(maskRoot, repeatedBuilder.buildPartial());
                        }
                        responseMsg = (T) builder.buildPartial();
                    } else if (responseMsg.hasField(maskRoot)) {
                        Message subMessage = (Message) responseMsg.getField(maskRoot);
                        Builder subBuilder = responseMsg.newBuilderForType();
                        FieldMaskUtil.merge(ctx.fieldMask, subMessage, subBuilder);
                        builder.setField(maskRoot, subBuilder.buildPartial());
                        responseMsg = (T) builder.buildPartial();
                    }
                }
            }
        }

        MediaType contentType = ctx.deriveTargetContentType();
        if (contentType != MediaType.JSON) {
            ctx.reportStatusCode(OK.code());
            return sendMessageResponse(OK, responseMsg);
        } else {
            ByteBuf body = ctx.nettyContext.alloc().buffer();
            try (ByteBufOutputStream channelOut = new ByteBufOutputStream(body)) {
                contentType = MediaType.JSON;
                String str = ctx.printJson(responseMsg);
                body.writeCharSequence(str, StandardCharsets.UTF_8);
            } catch (IOException e) {
                body.release();
                HttpResponseStatus status = INTERNAL_SERVER_ERROR;
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

    private ChannelFuture sendError(RouteContext ctx, HttpException t) {
        if (t instanceof InternalServerErrorException) {
            log.error("Internal server error while handling call", t);
        } else if (log.isDebugEnabled()) {
            log.debug("User error while handling call", t);
        }
        ExceptionMessage msg = t.toMessage();
        ctx.reportStatusCode(t.getStatus().code());
        return sendMessageResponse(t.getStatus(), msg);
    }

    private <T extends Message> ChannelFuture sendMessageResponse(HttpResponseStatus status, T responseMsg) {
        ByteBuf body = ctx.nettyContext.alloc().buffer();
        MediaType contentType = HttpRequestHandler.getAcceptType(ctx.nettyRequest);

        try {
            if (contentType == MediaType.PROTOBUF) {
                try (ByteBufOutputStream channelOut = new ByteBufOutputStream(body)) {
                    responseMsg.writeTo(channelOut);
                }
            } else if (contentType == MediaType.PLAIN_TEXT) {
                body.writeCharSequence(responseMsg.toString(), StandardCharsets.UTF_8);
            } else { // JSON by default
                contentType = MediaType.JSON;
                String str = ctx.printJson(responseMsg);
                body.writeCharSequence(str, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return HttpRequestHandler.sendPlainTextError(ctx.nettyContext, ctx.nettyRequest, INTERNAL_SERVER_ERROR,
                    e.toString());
        }
        HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, body);
        response.headers().set(CONTENT_TYPE, contentType.toString());
        response.headers().set(CONTENT_LENGTH, body.readableBytes());

        return HttpRequestHandler.sendResponse(ctx.nettyContext, ctx.nettyRequest, response);
    }
}
