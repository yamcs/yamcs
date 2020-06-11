package org.yamcs.client.base;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.yamcs.protobuf.ServerMessage;

import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;

public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger log = Logger.getLogger(WebSocketClientHandler.class.getName());

    private final WebSocketClientHandshaker handshaker;
    private final WebSocketClient client;

    private WebSocketClientCallback callback;

    private ChannelPromise handshakeFuture;

    public WebSocketClientHandler(WebSocketClientHandshaker handshaker, WebSocketClient client,
            WebSocketClientCallback callback) {
        this.handshaker = handshaker;
        this.client = client;
        this.callback = callback;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("WebSocket Client disconnected");
        client.completeAll();
        callback.disconnected();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            handshaker.finishHandshake(ch, (FullHttpResponse) msg);
            log.info("WebSocket Client connected");
            handshakeFuture.setSuccess();
            callback.connected();
            return;
        }

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                    "Unexpected FullHttpResponse (getStatus="
                            + response.status() + ", content="
                            + response.content().toString(CharsetUtil.UTF_8)
                            + ')');
        }

        WebSocketFrame frame = (WebSocketFrame) msg;
        if (frame instanceof BinaryWebSocketFrame) {
            BinaryWebSocketFrame binaryFrame = (BinaryWebSocketFrame) frame;
            if (log.isLoggable(Level.FINEST)) {
                log.finest("WebSocket Client received message of size " + binaryFrame.content().readableBytes());
            }
            handleFrame(binaryFrame);
        } else if (frame instanceof PingWebSocketFrame) {
            frame.content().retain();
            ch.writeAndFlush(new PongWebSocketFrame(frame.content()));
        } else if (frame instanceof PongWebSocketFrame) {
            log.info("WebSocket Client received pong");
        } else if (frame instanceof CloseWebSocketFrame) {
            log.info("WebSocket Client received closing");
            ch.close();
        } else {
            log.severe("Received unsupported web socket frame " + frame);
            System.out.println(((TextWebSocketFrame) frame).text());
        }
    }

    private void handleFrame(BinaryWebSocketFrame frame) {
        try (InputStream in = new ByteBufInputStream(frame.content())) {
            ServerMessage message = ServerMessage.newBuilder().mergeFrom(in).build();
            if ("reply".equals(message.getType())) {
                client.handleReply(message);
            } else {
                client.handleMessage(message);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "WebSocket exception. Closing channel", cause);
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
        callback.connectionFailed(cause);
    }
}
