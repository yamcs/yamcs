package org.yamcs.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.api.Observer;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.CancelOptions;
import org.yamcs.protobuf.ClientMessage;
import org.yamcs.protobuf.Reply;
import org.yamcs.protobuf.ServerMessage;
import org.yamcs.protobuf.State;
import org.yamcs.security.User;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.Any;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.HandshakeComplete;

public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Log log = new Log(WebSocketFrameHandler.class);

    private HttpServer httpServer;

    private HttpRequest nettyRequest;
    private boolean protobuf;
    private User user;

    // after how many consecutive dropped writes will the connection be closed
    private int connectionCloseNumDroppedMsg;

    private SocketAddress remoteAddress;
    private WriteBufferWaterMark writeBufferWaterMark;

    private List<TopicContext> contexts = new ArrayList<>();
    private Map<Integer, Observer<Message>> clientObserversByCall = new HashMap<>();

    public WebSocketFrameHandler(HttpServer httpServer, HttpRequest req, User user, int connectionCloseNumDroppedMsg,
            WriteBufferWaterMark writeBufferWaterMark) {
        this.httpServer = httpServer;
        this.nettyRequest = req;
        this.user = user;
        this.connectionCloseNumDroppedMsg = connectionCloseNumDroppedMsg;
        this.writeBufferWaterMark = writeBufferWaterMark;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext nettyContext) throws Exception {
        nettyContext.channel().config().setWriteBufferWaterMark(writeBufferWaterMark);

        // Store this information, because it will be null when the channel is disconnected
        remoteAddress = nettyContext.channel().remoteAddress();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext nettyContext, Object evt) throws Exception {
        if (evt instanceof HandshakeComplete) {
            HandshakeComplete handshakeEvt = (HandshakeComplete) evt;
            String subprotocol = handshakeEvt.selectedSubprotocol();
            protobuf = "protobuf".equals(subprotocol);

            if (protobuf) {
                log.info("{} {} {} [subprotocol: protobuf]", nettyRequest.method(), nettyRequest.uri(),
                        HttpResponseStatus.SWITCHING_PROTOCOLS.code());
            } else {
                log.info("{} {} {} [subprotocol: json]", nettyRequest.method(), nettyRequest.uri(),
                        HttpResponseStatus.SWITCHING_PROTOCOLS.code());
            }

            // After upgrade, no further HTTP messages will be received
            nettyContext.pipeline().remove(HttpRequestHandler.class);
        } else {
            super.userEventTriggered(nettyContext, evt);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext nettyContext, WebSocketFrame frame) throws Exception {
        ClientMessage message;
        if (protobuf) {
            try (InputStream in = new ByteBufInputStream(frame.content())) {
                message = ClientMessage.newBuilder().mergeFrom(in).build();
            }
        } else {
            String json = frame.content().toString(StandardCharsets.UTF_8);
            JsonObject obj = new JsonParser().parse(json).getAsJsonObject();

            String messageType = obj.get("type").getAsString();
            switch (messageType) {
            case "state":
                message = jsonToClientMessage(obj, null);
                break;
            case "cancel":
                message = jsonToClientMessage(obj, CancelOptions.getDescriptor());
                break;
            default:
                Topic topic = matchTopic(messageType);
                if (topic == null) {
                    message = jsonToClientMessage(obj, null);
                    break;
                }

                message = jsonToClientMessage(obj, topic.getRequestPrototype().getDescriptorForType());
            }
        }

        try {
            switch (message.getType()) {
            case "state":
                dumpState(nettyContext);
                break;
            case "cancel":
                cancelCall(nettyContext, message);
                break;
            default:
                Topic topic = matchTopic(message.getType());
                if (topic == null) {
                    throw new NotFoundException("No such topic");
                }
                if (message.getCall() > 0) {
                    streamToExistingCall(nettyContext, message, topic);
                } else {
                    startNewContext(nettyContext, message, topic);
                }
            }
        } catch (HttpException e) {
            Reply reply = Reply.newBuilder()
                    .setReplyTo(message.getId())
                    .setException(e.toMessage())
                    .build();
            writeMessage(nettyContext, ServerMessage.newBuilder()
                    .setType("reply")
                    .setData(Any.pack(reply, HttpServer.TYPE_URL_PREFIX))
                    .build());
        }
    }

    private ClientMessage jsonToClientMessage(JsonObject obj, Descriptor optionsDescriptor)
            throws InvalidProtocolBufferException {
        if (obj.has("options")) {
            if (optionsDescriptor == null) {
                // We don't know what type the options are. Remove it so we can parse the JSON
                // and handle the exception.
                // This typically happens because the client specifies an unknown "type" field.
                obj.remove("options");
            } else {
                // Inject @type property, as required by JsonFormat when parsing Any fields.
                // We prefer to inject it on the server, because there we have no need to
                // enforce it on the client (each topic has a unique message).
                JsonObject dataObject = obj.get("options").getAsJsonObject();
                String fullName = optionsDescriptor.getFullName();
                dataObject.addProperty("@type", HttpServer.TYPE_URL_PREFIX + "/" + fullName);
            }
        }

        ClientMessage.Builder msgb = ClientMessage.newBuilder();
        httpServer.getJsonParser().merge(obj.toString(), msgb);
        return msgb.build();
    }

    private void dumpState(ChannelHandlerContext nettyContext) throws IOException {
        State.Builder stateb = State.newBuilder();
        for (TopicContext ctx : contexts) {
            stateb.addCalls(ctx.dumpState());
        }
        writeMessage(nettyContext, ServerMessage.newBuilder()
                .setType("state")
                .setData(Any.pack(stateb.build(), HttpServer.TYPE_URL_PREFIX))
                .build());
    }

    private void cancelCall(ChannelHandlerContext nettyContext, ClientMessage clientMessage)
            throws InvalidProtocolBufferException {
        if (clientMessage.hasOptions()) {
            CancelOptions options = clientMessage.getOptions().unpack(CancelOptions.class);
            int callId = options.getCall();
            for (TopicContext ctx : new ArrayList<>(contexts)) {
                if (ctx.getId() == callId) {
                    ctx.close();
                    clientObserversByCall.remove(callId);
                }
            }
        }
    }

    private void startNewContext(ChannelHandlerContext nettyContext, ClientMessage clientMessage, Topic topic)
            throws InvalidProtocolBufferException {
        TopicContext ctx = new TopicContext(httpServer, nettyContext, user, clientMessage, topic);

        Message requestPrototype = topic.getRequestPrototype();

        Message apiRequest = requestPrototype.getDefaultInstanceForType();
        if (clientMessage.hasOptions()) {
            apiRequest = clientMessage.getOptions().unpack(requestPrototype.getClass());
        }

        WebSocketObserver observer = new WebSocketObserver(ctx, this);
        ctx.addListener(cancellationCause -> {
            observer.cancelCall(cancellationCause != null ? cancellationCause.getMessage() : null);
        });

        contexts.add(ctx);

        if (ctx.isClientStreaming()) {
            Observer<Message> clientObserver = topic.callMethod(ctx, observer);
            clientObserversByCall.put(ctx.getId(), clientObserver);
            clientObserver.next(apiRequest);
        } else {
            topic.callMethod(ctx, apiRequest, observer);
        }

        observer.sendReply(Reply.newBuilder().setReplyTo(clientMessage.getId()).build());
    }

    private void streamToExistingCall(ChannelHandlerContext nettyContext, ClientMessage clientMessage, Topic topic)
            throws InvalidProtocolBufferException {
        Observer<Message> clientObserver = clientObserversByCall.get(clientMessage.getCall());
        if (clientObserver == null) {
            throw new BadRequestException("Cannot find matching call");
        }

        Message requestPrototype = topic.getRequestPrototype();

        Message apiRequest = requestPrototype.getDefaultInstanceForType();
        if (clientMessage.hasOptions()) {
            apiRequest = clientMessage.getOptions().unpack(requestPrototype.getClass());
        }
        clientObserver.next(apiRequest);
    }

    void writeMessage(ChannelHandlerContext nettyContext, ServerMessage serverMessage) throws IOException {
        if (protobuf) {
            ByteBuf buf = nettyContext.alloc().buffer();
            try (ByteBufOutputStream bufOut = new ByteBufOutputStream(buf)) {
                serverMessage.writeTo(bufOut);
            }
            nettyContext.channel().writeAndFlush(new BinaryWebSocketFrame(buf));
        } else {
            String json = httpServer.getJsonPrinter().print(serverMessage);
            nettyContext.channel().writeAndFlush(new TextWebSocketFrame(json));
        }
    }

    /**
     * Called when the client abruptly closes the connection
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext nettyContext, Throwable cause) throws Exception {
        log.warn("Closing channel due to error", cause);
        nettyContext.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext nettyContext) throws Exception {
        log.info("Channel {} closed", remoteAddress);
        contexts.forEach(TopicContext::close);
        contexts.clear();
    }

    private Topic matchTopic(String topicName) {
        for (Topic topic : httpServer.getTopics()) {
            if (topicName.equals(topic.getName())) {
                return topic;
            }
        }
        return null;
    }
}
