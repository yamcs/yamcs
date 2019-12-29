package org.yamcs.http.api;

import org.yamcs.logging.Log;

import com.google.protobuf.Message;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpRequest;

public class StreamingClientHandler extends SimpleChannelInboundHandler<Message> {

    private static final Log log = new Log(StreamingClientHandler.class);
    private HttpRequest req;

    public StreamingClientHandler(HttpRequest req) {
        this.req = req;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        // System.out.println("Got a message " + msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        /*-
        if (errorState) {
            return;
        }
        errorState = true;
        log.warn("Exception caught in the table load pipeline, closing the connection: {}", cause.getMessage());
        inputStream.close();
        if (cause instanceof DecoderException) {
            Throwable t = cause.getCause();
            sendErrorAndCloseAfter2Seconds(ctx, HttpResponseStatus.BAD_REQUEST, t.toString());
        } else {
            sendErrorAndCloseAfter2Seconds(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, cause.toString());
        }
        */
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object obj) throws Exception {
        /*-
        if (obj == HttpRequestHandler.CONTENT_FINISHED_EVENT) {
            inputStream.close();
            TableLoadResponse tlr = TableLoadResponse.newBuilder().setRowsLoaded(count).build();
            HttpRequestHandler.sendMessageResponse(ctx, req, HttpResponseStatus.OK, tlr);
        }
        */
    }

    /*-
    void sendErrorAndCloseAfter2Seconds(ChannelHandlerContext ctx, HttpResponseStatus status, String msg) {
        RestExceptionMessage.Builder exb = RestExceptionMessage.newBuilder().setType("TableLoadError").setMsg(msg);
        exb.setExtension(Table.rowsLoaded, count);
        HttpRequestHandler.sendMessageResponse(ctx, req, status, exb.build(), false).addListener(f -> {
            // schedule close after 2 seconds so the client has the chance to read the error message
            // see https://groups.google.com/forum/#!topic/netty/eVB6SMcXOHI
            ctx.executor().schedule(() -> {
                ctx.close();
            }, 2, TimeUnit.SECONDS);
        });
    }
    */
}
