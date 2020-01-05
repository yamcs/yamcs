package org.yamcs.http;

import org.yamcs.api.Observer;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;

public class StreamingClientHandler extends SimpleChannelInboundHandler<Message> {

    private RouteContext ctx;
    private Message requestPrototype;
    private Observer<Message> clientObserver;

    private boolean errorState = false;

    public StreamingClientHandler(RouteContext ctx) {
        this.ctx = ctx;

        MethodDescriptor method = ctx.getMethod();
        if (ctx.isServerStreaming()) {
            Observer<Message> responseObserver = new ServerStreamingObserver(ctx);
            clientObserver = ctx.getApi().callMethod(method, ctx, responseObserver);
        } else {
            Observer<Message> responseObserver = new CallObserver(ctx);
            clientObserver = ctx.getApi().callMethod(method, ctx, responseObserver);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext nettyContext, Message msg) throws Exception {
        if (errorState) {
            return;
        }

        if (requestPrototype == null) {
            requestPrototype = HttpTranscoder.transcode(ctx);
        }

        Message.Builder b = requestPrototype.toBuilder();

        String body = ctx.getBodySpecifier();
        if (body == null || "*".equals(body)) {
            b.mergeFrom(msg);
        } else {
            FieldDescriptor field = ctx.getRequestPrototype().getDescriptorForType().findFieldByName(body);
            b.setField(field, msg);
        }

        clientObserver.next(b.build());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (errorState) {
            return;
        }

        errorState = true;
        if (cause instanceof DecoderException) {
            cause = cause.getCause();
            clientObserver.completeExceptionally(new BadRequestException(cause));
        } else {
            clientObserver.completeExceptionally(cause);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object obj) throws Exception {
        if (obj == HttpRequestHandler.CONTENT_FINISHED_EVENT) {
            clientObserver.complete();
        }
    }
}
