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
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.web.rest.RestException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Handles handshakes and messages
 */
public class WebSocketServerHandler {

    final static Logger log=LoggerFactory.getLogger(WebSocketServerHandler.class.getName());

    public static final String WEBSOCKET_PATH = "_websocket";
    private WebSocketServerHandshaker handshaker;
    private int dataSeqCount=-1;

    private WebSocketDecoder jsonDecoder = new JsonDecoder();
    private JsonEncoder jsonEncoder = new JsonEncoder();
    
    //these two are valid after the socket has been upgraded and they are practical final
    Channel channel;
    WebSocketChannelClient channelClient;
    
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
            log.debug("received websocket frame {}", frame);
            // Check for closing frame
            if (frame instanceof CloseWebSocketFrame) {
                this.handshaker.close(ctx.getChannel(), (CloseWebSocketFrame) frame);
                return;
            } else if (frame instanceof PingWebSocketFrame) {
                ctx.getChannel().write(new PongWebSocketFrame(frame.getBinaryData()));
                return;
            } else if (!(frame instanceof TextWebSocketFrame)) {
                throw new WebSocketException(WSConstants.NO_REQUEST_ID, String.format("%s frame types not supported", frame.getClass().getName()));
            }

            ChannelBuffer binary = frame.getBinaryData();
            if (binary != null) {
                InputStream in = new ChannelBufferInputStream(binary);
                WebSocketDecodeContext msg = jsonDecoder.decodeMessageWrapper(in);
                String resource = msg.getResource();
                if ("parameter".equals(resource) || "request".equals(resource)) {
                    channelClient.getParameterClient().processRequest(msg, in);
                } else if ("cmdhistory".equals(resource)) {
                    channelClient.getCommandHistoryClient().processRequest(msg, in);
                } else {
                    throw new WebSocketException(msg.getRequestId(), "Invalid message (unsupported resource: '"+resource+"')");
                }
            }
        } catch (StructuredWebSocketException e) {
            sendException(e.getRequestId(), e.getMessage(), e.getData(), e.getSchema());
        } catch (WebSocketException e) {
            sendException(e.getRequestId(), e.getMessage());
        }
    }

    public WebSocketDecoder getJsonDecoder() { // TODO improve
        return jsonDecoder;
    }

    private String getWebSocketLocation(String yamcsInstance, HttpRequest req) {
        return "ws://" + req.getHeader(HttpHeaders.Names.HOST) + "/"+ yamcsInstance+"/"+WEBSOCKET_PATH;
    }
    
    /**
     * sends a generic string exception message
     * @param requestId the request that has generated the exception
     * @param message the exception message
     */
    private void sendException(int requestId, String message) {
        try {
            String msg = jsonEncoder.encodeException(requestId, message);
            channel.write(new TextWebSocketFrame(msg));
        } catch (WebSocketException e) { // Well, that's embarassing. Send plain text
            log.error("Could not encode generic JSON exception", e);
            channel.write(new TextWebSocketFrame("Internal Server Error"));
        }
    }

    /**
     * writes a structured exception message (e.g. for InvalidIdentificationException 
     * we want to pass the names of the invalid parameters)
     */
    private <T> void sendException(int requestId, String exceptionType, T message, Schema<T> schema) {
        try {
            String msg = jsonEncoder.encodeException(requestId, exceptionType, message, schema);
            channel.write(new TextWebSocketFrame(msg));
        } catch (WebSocketException e) {
            log.error("Could not encode structured JSON exception for exception data: " + message + ". Sending generic exception instead");
            sendException(requestId, e.getMessage());
        }
    }

    public void sendAckReply(int requestId) {
        try {
            String msg = jsonEncoder.encodeAckReply(requestId);
            channel.write(new TextWebSocketFrame(msg));
        } catch (WebSocketException e) {
            sendException(requestId, e.getMessage());
        }
    }

    /**
     * Sends actual data over the web socket
     */
    public <T> void sendData(ProtoDataType dataType, T data, Schema<T> schema) throws IOException, RestException {
        dataSeqCount++;
        if(!channel.isOpen()) throw new IOException("Channel not open");
        
        if(!channel.isWritable()) {
            log.warn("Dropping message because channel is not writable");
            return;
        }

        String msg = jsonEncoder.encodeData(dataSeqCount, dataType, data, schema);
        channel.write(new TextWebSocketFrame(msg));
    }
    
    public void channelDisconnected(Channel c) {
        if(channelClient!=null) {
            log.info("Channel "+c.getRemoteAddress()+" disconnected");
            channelClient.quit();
        }
    }
}
