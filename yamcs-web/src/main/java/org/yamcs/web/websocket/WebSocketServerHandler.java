package org.yamcs.web.websocket;

import com.dyuproject.protostuff.Schema;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.websocketx.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.protobuf.Websocket.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.ProtoDataType;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

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
    WebSocketChannelClient channelClient;

    // Provides access to the various resources served through this websocket
    private Map<String, AbstractWebSocketResource> resourcesByName = new HashMap<>();
    
    public void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest req, MessageEvent e, String yamcsInstance) throws Exception {
        //TODO: can we ever reach this twice???
        if(channelClient==null) {
            String applicationName = determineApplicationName(req);
            this.channelClient=new WebSocketChannelClient(yamcsInstance, this, applicationName);
        }

        this.channel=ctx.getChannel();

        // Handshake
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(this.getWebSocketLocation(yamcsInstance, req), null, false);
        this.handshaker = wsFactory.newHandshaker(req);
        if (this.handshaker == null) {
            wsFactory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel());
        } else {
            this.handshaker.handshake(ctx.getChannel(), req).addListener(WebSocketServerHandshaker.HANDSHAKE_LISTENER);
        }
    }

    /**
     * Tries to use an application name as provided by the client. Special logic is needed for uss-web, since
     * JS WebSocket API doesn't support custom http headers. We should maybe think of making this part of our
     * protocol instead.
     */
    private String determineApplicationName(HttpRequest req) {
        if (req.containsHeader(HttpHeaders.Names.USER_AGENT)) {
            String userAgent = req.getHeader(HttpHeaders.Names.USER_AGENT);
            return (userAgent.contains("Mozilla")) ? "uss-web" : userAgent;
        } else {
            // Origin is always present, according to spec.
            return "Unknown (" + req.getHeader(HttpHeaders.Names.ORIGIN) +")";
        }
    }

    public void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        try {
            try {
                log.debug("received websocket frame {}", frame);
                // Check for closing frame
                if (frame instanceof CloseWebSocketFrame) {
                    this.handshaker.close(ctx.getChannel(), (CloseWebSocketFrame) frame);
                    return;
                } else if (frame instanceof PingWebSocketFrame) {
                    ctx.getChannel().write(new PongWebSocketFrame(frame.getBinaryData()));
                    return;
                } else if (frame instanceof TextWebSocketFrame) {
                    // We could do something more clever here, but only need to support json and gpb for now
                    if (decoder == null)
                        decoder = new JsonDecoder();
                    if (encoder == null)
                        encoder = new JsonEncoder();
                } else if (frame instanceof  BinaryWebSocketFrame) {
                    if (decoder == null)
                        decoder = new ProtobufDecoder();
                    if (encoder == null)
                        encoder = new ProtobufEncoder();
                } else {
                    throw new WebSocketException(WSConstants.NO_REQUEST_ID, String.format("%s frame types not supported", frame.getClass().getName()));
                }

                ChannelBuffer binary = frame.getBinaryData();
                if (binary != null) {
                    InputStream in = new ChannelBufferInputStream(binary);
                    WebSocketDecodeContext msg = decoder.decodeMessage(in);
                    AbstractWebSocketResource resource = resourcesByName.get(msg.getResource());
                    if (resource != null) {
                        WebSocketReplyData reply = resource.processRequest(msg, decoder);
                        sendReply(reply);
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
        return "ws://" + req.getHeader(HttpHeaders.Names.HOST) + "/"+ yamcsInstance+"/"+WEBSOCKET_PATH;
    }

    private void sendReply(WebSocketReplyData reply) throws IOException {
        WebSocketFrame frame = encoder.encodeReply(reply);
        channel.write(frame);
    }

    private void sendException(WebSocketException e) throws IOException {
        WebSocketFrame frame = encoder.encodeException(e);
        channel.write(frame);
    }

    /**
     * Sends actual data over the web socket
     */
    public <T> void sendData(ProtoDataType dataType, T data, Schema<T> schema) throws IOException {
        dataSeqCount++;
        if(!channel.isOpen()) throw new IOException("Channel not open");
        
        if(!channel.isWritable()) {
            log.warn("Dropping message because channel is not writable");
            return;
        }
        WebSocketFrame frame = encoder.encodeData(dataSeqCount, dataType, data, schema);
        channel.write(frame);
    }
    
    public void channelDisconnected(Channel c) {
        if(channelClient!=null) {
            log.info("Channel "+c.getRemoteAddress()+" disconnected");
            channelClient.quit();
        }
    }
}
