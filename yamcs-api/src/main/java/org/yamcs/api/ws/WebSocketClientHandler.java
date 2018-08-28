package org.yamcs.api.ws;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.ws.WebSocketClient.RequestResponsePair;
import org.yamcs.protobuf.Web.WebSocketServerMessage;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketExceptionData;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketReplyData;

import com.google.protobuf.InvalidProtocolBufferException;

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

    private static final Logger log = LoggerFactory.getLogger(WebSocketClientHandler.class);

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
        callback.disconnected();

        if (client.isReconnectionEnabled()) {
            ctx.channel().eventLoop().schedule(() -> client.connect(), client.reconnectionInterval,
                    TimeUnit.MILLISECONDS);
        }
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
            log.trace("WebSocket Client received message of size {} ", binaryFrame.content().readableBytes());
            processFrame(binaryFrame);
        } else if (frame instanceof PingWebSocketFrame) {
            frame.content().retain();
            ch.writeAndFlush(new PongWebSocketFrame(frame.content()));
        } else if (frame instanceof PongWebSocketFrame) {
            log.info("WebSocket Client received pong");
        } else if (frame instanceof CloseWebSocketFrame) {
            log.info("WebSocket Client received closing");
            ch.close();
        } else {
            log.error("Received unsupported web socket frame " + frame);
            System.out.println(((TextWebSocketFrame) frame).text());
        }
    }

    private void processFrame(BinaryWebSocketFrame frame) {
        try {
            WebSocketServerMessage message = WebSocketServerMessage.newBuilder()
                    .mergeFrom(new ByteBufInputStream(frame.content())).build();
            switch (message.getType()) {
            case REPLY:
                processReplyData(message.getReply());
                break;
            case EXCEPTION:
                processExceptionData(message.getException());
                break;
            case DATA:
                callback.onMessage(message.getData());
                break;
            default:
                throw new IllegalStateException("Invalid message type received: " + message.getType());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processReplyData(WebSocketReplyData reply) throws InvalidProtocolBufferException {
        int reqId = reply.getSequenceNumber();
        RequestResponsePair pair = client.removeUpstreamRequest(reqId);
        if (pair == null) {
            log.warn("Received an exception for a request I did not send (or was already finished) seqNum: {}", reqId);
            return;
        }
        if (pair.responseHandler != null) {
            pair.responseHandler.onCompletion(reply);
        }
    }

    private void processExceptionData(WebSocketExceptionData exceptionData) throws IOException {
        int reqId = exceptionData.getSequenceNumber();
        RequestResponsePair pair = client.getRequestResponsePair(reqId);
        if (pair == null) {
            log.warn("Received an exception for a request I did not send (or was already finished) seqNum: {}", reqId);
            return;
        }

        log.warn("Got exception message " + exceptionData.getMessage());
        if (pair.responseHandler != null) {
            pair.responseHandler.onException(exceptionData);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket exception. Closing channel", cause);
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
        callback.connectionFailed(cause);
    }
}
