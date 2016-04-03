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
import org.yamcs.web.HttpRequestHandler;
import org.yamcs.web.HttpRequestInfo;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.ServerHandshakeStateEvent;
import io.netty.util.AttributeKey;
import io.protostuff.Schema;

/**
 * Class for text/binary websocket handling
 */
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    public static final String WEBSOCKET_PATH = "_websocket";
    public static final AttributeKey<HttpRequestInfo> CTX_HTTP_REQUEST_INFO = AttributeKey.valueOf("httpRequestInfo");

    private static final Logger log = LoggerFactory.getLogger(WebSocketFrameHandler.class);

    private ChannelHandlerContext ctx;

    //these two are valid after the socket has been upgraded and they are practical final
    private Channel channel;
    private WebSocketProcessorClient processorClient;

    private WebSocketDecoder decoder;
    private WebSocketEncoder encoder;

    private int dataSeqCount = -1;
    private int droppedWrites = 0; // Consecutive dropped writes used to free up resources

    // Basic info about the original http request that lead to the websocket upgrade
    private HttpRequestInfo originalRequestInfo;

    // Provides access to the various resources served through this websocket
    private Map<String, AbstractWebSocketResource> resourcesByName = new HashMap<>();

    public WebSocketFrameHandler(HttpRequestInfo originalRequestInfo) {
        this.originalRequestInfo = originalRequestInfo;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        channel = ctx.channel();

        // Try to use an application name as provided by the client. For browsers (since overriding
        // websocket headers is not supported) this will be the browser's standard user-agent string
        String applicationName;
        if (originalRequestInfo.getHeaders().contains(HttpHeaders.Names.USER_AGENT)) {
            applicationName = originalRequestInfo.getHeaders().get(HttpHeaders.Names.USER_AGENT);
        } else {
            applicationName = "Unknown (" + channel.remoteAddress() +")";
        }

        String yamcsInstance = originalRequestInfo.getYamcsInstance();
        AuthenticationToken authToken = originalRequestInfo.getAuthenticationToken();
        processorClient = new WebSocketProcessorClient(yamcsInstance, this, applicationName, authToken);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
            log.info("{} {} {}", originalRequestInfo.getMethod(), originalRequestInfo.getUri(), HttpResponseStatus.SWITCHING_PROTOCOLS.code());

            // After upgrade, no further HTTP messages will be received
            ctx.pipeline().remove(HttpRequestHandler.class);
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        try {
            try {
                log.debug("Received frame {}", frame);
                if (frame instanceof TextWebSocketFrame) {
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
                    // Pong, ping, continuation and close should already be handled by netty's handler
                    return;
                }

                ByteBuf binary = frame.content();
                if (binary != null) {
                    InputStream in = new ByteBufInputStream(binary);
                    WebSocketDecodeContext msg = decoder.decodeMessage(in);
                    AbstractWebSocketResource resource = resourcesByName.get(msg.getResource());
                    if (resource != null) {
                        WebSocketReplyData reply = resource.processRequest(msg, decoder, processorClient.getAuthToken());
                        if(reply != null) {
                            sendReply(reply);
                        }
                    } else {
                        throw new WebSocketException(msg.getRequestId(), "Invalid message (unsupported resource: '"+msg.getResource()+"')");
                    }
                }
            } catch (WebSocketException e) {
                log.debug("Returning nominal exception back to the client: {}", e.getMessage());
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

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Will close channel due to internal error", cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (processorClient != null) {
            log.info("Channel " + ctx.channel().remoteAddress() + " closed");
            processorClient.quit();
        }
    }

    void addResource(String name, AbstractWebSocketResource resource) {
        if (resourcesByName.containsKey(name)) {
            throw new ConfigurationException("A resource named '" + name + "' is already being served");
        }
        resourcesByName.put(name, resource);
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
     * Sends actual data over the web socket. If the channel is not or no longer
     * writable, the message is dropped. TODO A better approach could be to await the
     * write for a little while, like we do for chunked writes, but doing so
     * in-thread would currently cause other yamcs-level subscribers to block as
     * well for certain types of requests. Needs work.
     */
    public <S> void sendData(ProtoDataType dataType, S data, Schema<S> schema) throws IOException {
        dataSeqCount++;
        if(!channel.isOpen()) throw new IOException("Channel not open");

        if(!channel.isWritable()) {
            log.warn("Dropping {} message for client [id={}, username={}] because channel is not or no longer writable",
                    dataType, processorClient.getClientId(), processorClient.getUsername());
            droppedWrites++;

            if (droppedWrites == 5) {
                log.warn("Too many failed writes for client [id={}, username={}]. Forcing disconnect",
                        processorClient.getClientId(), processorClient.getUsername());
                ctx.close();
            }
            return;
        }
        droppedWrites = 0;
        WebSocketFrame frame = encoder.encodeData(dataSeqCount, dataType, data, schema);
        channel.writeAndFlush(frame);
    }


}
