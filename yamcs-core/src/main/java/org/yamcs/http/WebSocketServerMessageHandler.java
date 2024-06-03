package org.yamcs.http;

import org.yamcs.protobuf.ServerMessage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/**
 * Encodes {@link ServerMessage} to either {@link BinaryWebSocketFrame} or {@link TextWebSocketFrame} depending if the
 * protobuf or json has to be sent.
 */
public class WebSocketServerMessageHandler extends ChannelOutboundHandlerAdapter {

    final boolean protobuf;
    final HttpServer httpServer;

    public WebSocketServerMessageHandler(HttpServer httpServer, boolean protobuf) {
        this.httpServer = httpServer;
        this.protobuf = protobuf;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ServerMessage serverMessage = (ServerMessage) msg;
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

        ctx.write(frame, promise);
    }
}
