package org.yamcs.web.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.api.ws.WSConstants;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.security.AuthenticationToken;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.protostuff.Schema;

/**
 * Handles handshakes and messages
 */
public class WebSocketServerHandler {

    final static Logger log=LoggerFactory.getLogger(WebSocketServerHandler.class.getName());

    public static final String WEBSOCKET_PATH = "_websocket";
    private WebSocketServerHandshaker handshaker;
    private int dataSeqCount=-1;

    private WebSocketDecoder decoder;
    private WebSocketEncoder encoder;

    //these two are valid after the socket has been upgraded and they are practical final
    Channel channel;
    WebSocketProcessorClient processorClient;

    // Provides access to the various resources served through this websocket
    private Map<String, AbstractWebSocketResource> resourcesByName = new HashMap<>();

    public void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest req, String yamcsInstance, AuthenticationToken authToken) throws Exception {
        if(!(req instanceof FullHttpRequest)) throw new RuntimeException("Full HTTP request expected");
        if(processorClient==null) {
            String applicationName = determineApplicationName(ctx, req);
            this.processorClient=new WebSocketProcessorClient(yamcsInstance, this, applicationName, authToken);
        }
        
        this.channel=ctx.channel();
        
        // Handshake
        log.debug("Upgrading {} to WebSocket", ctx.channel().remoteAddress());
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(this.getWebSocketLocation(yamcsInstance, req), null, false);
        this.handshaker = wsFactory.newHandshaker(req);
        if (this.handshaker == null) {
            log.info("{} {} {}", req.getMethod(), req.getUri(), HttpResponseStatus.UPGRADE_REQUIRED.code());
            WebSocketServerHandshakerFactory.sendUnsupportedWebSocketVersionResponse(ctx.channel());
        } else {
            log.info("{} {} {}", req.getMethod(), req.getUri(), HttpResponseStatus.SWITCHING_PROTOCOLS.code());
            this.handshaker.handshake(ctx.channel(), (FullHttpRequest)req);
        }
    }

    /**
     * Tries to use an application name as provided by the client. Special logic is needed for uss-web, since
     * JS WebSocket API doesn't support custom http headers. We should maybe think of making this part of our
     * protocol instead.
     */
    private String determineApplicationName(ChannelHandlerContext ctx, HttpRequest req) {
        if (req.headers().contains(HttpHeaders.Names.USER_AGENT)) {
            String userAgent = req.headers().get(HttpHeaders.Names.USER_AGENT);
            return (userAgent.contains("Mozilla")) ? "yamcs-web" : userAgent;
        } else {
            return "Unknown (" + ctx.channel().remoteAddress() +")";
        }
    }

    public void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        try {
            try {
                log.debug("Received frame {}", frame);
                if (frame instanceof CloseWebSocketFrame) {
                    processorClient.quit();
                    this.handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
                    return;
                } else if (frame instanceof PingWebSocketFrame) {
                    ctx.channel().writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
                    return;
                } else if (frame instanceof TextWebSocketFrame) {
                    // We could do something more clever here, but only need to support json and gpb for now
                    if (decoder == null)
                        decoder = new JsonDecoder();
                    if (encoder == null)
                        encoder = new JsonEncoder();
                } else if (frame instanceof BinaryWebSocketFrame) {
                    if (decoder == null)
                        decoder = new ProtobufDecoder();
                    if (encoder == null)
                        encoder = new ProtobufEncoder(ctx);
                } else {
                    throw new WebSocketException(WSConstants.NO_REQUEST_ID, String.format("%s frame types not supported", frame.getClass().getName()));
                }
                
                ByteBuf binary = frame.content();
                if (binary != null) {
                    InputStream in = new ByteBufInputStream(binary);
                    WebSocketDecodeContext msg = decoder.decodeMessage(in);
                    AbstractWebSocketResource resource = resourcesByName.get(msg.getResource());
                    if (resource != null) {
                        WebSocketReplyData reply = resource.processRequest(msg, decoder, processorClient.getAuthToken());
                        if(reply!=null) {
                            sendReply(reply);
                        }
                    } else {
                        throw new WebSocketException(msg.getRequestId(), "Invalid message (unsupported resource: '"+msg.getResource()+"')");
                    }
                }
            } catch (WebSocketException e) {
                log.debug("Returning nominal exception back to the client", e);
                sendException(e);
            }
        } catch (Exception e) {
            log.error("Internal Server Error while handling incoming web socket frame", e);
            try { // Gut-shot, at least try to inform the client
                // TODO should do our best to return a better requestId here
                sendException(new WebSocketException(WSConstants.NO_REQUEST_ID, "Internal Server Error"));
            } catch(Exception e2) { // Oh well, we tried.
                log.warn("Could not inform client of earlier Internal Server Error due to additional exception " + e2, e2);
            }
        }
    }

    void addResource(String name, AbstractWebSocketResource resource) {
        if (resourcesByName.containsKey(name)) {
            throw new ConfigurationException("A resource named '" + name + "' is already being served");
        }
        resourcesByName.put(name, resource);
    }

    private String getWebSocketLocation(String yamcsInstance, HttpRequest req) {
        return "ws://" + req.headers().get(HttpHeaders.Names.HOST) + "/"+ yamcsInstance+"/"+WEBSOCKET_PATH;
    }

    public void sendReply(WebSocketReplyData reply) throws IOException {
        if(!channel.isOpen()) throw new IOException("Channel not open");
        if(!channel.isWritable()) {
            log.warn("Dropping reply message because channel is not writable");
            return;
        }
        
        WebSocketFrame frame = encoder.encodeReply(reply);
        channel.writeAndFlush(frame);
    }

    private void sendException(WebSocketException e) throws IOException {
        WebSocketFrame frame = encoder.encodeException(e);
        channel.writeAndFlush(frame);
    }

    /**
     * Sends actual data over the web socket
     */
    public <T> void sendData(ProtoDataType dataType, T data, Schema<T> schema) throws IOException {
        dataSeqCount++;
        if(!channel.isOpen()) throw new IOException("Channel not open");
        
        if(!channel.isWritable()) {
            log.warn("Dropping " + dataType + " message because channel is not writable");
            return;
        }
        WebSocketFrame frame = encoder.encodeData(dataSeqCount, dataType, data, schema);
        channel.writeAndFlush(frame);
    }

    public void channelDisconnected(Channel c) {
        if(processorClient!=null) {
            log.info("Channel "+c.remoteAddress()+" disconnected");
            processorClient.quit();
        }
    }
}
