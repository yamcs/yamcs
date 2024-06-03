package org.yamcs.client.base;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

import org.yamcs.api.ExceptionMessage;
import org.yamcs.api.Observer;
import org.yamcs.client.ClientException;
import org.yamcs.client.ClientException.ExceptionData;
import org.yamcs.protobuf.CancelOptions;
import org.yamcs.protobuf.ClientMessage;
import org.yamcs.protobuf.Reply;
import org.yamcs.protobuf.ServerMessage;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.concurrent.Future;

/**
 * Netty-implementation of a Yamcs web socket client.
 */
public class WebSocketClient {

    private static final Logger log = Logger.getLogger(WebSocketClient.class.getName());
    private Level messageLogging = Level.FINEST;

    private String host;
    private int port;
    private boolean tls;
    private String context;

    private WebSocketClientCallback callback;

    private EventLoopGroup group = new NioEventLoopGroup(1);
    private Channel nettyChannel;
    private String userAgent;
    private boolean allowCompression = true;
    private Integer timeoutMs;

    private boolean tcpKeepAlive;
    private boolean insecureTls;
    private KeyStore caKeyStore;

    private int maxFramePayloadLength = 65536;

    private AtomicInteger idSequence = new AtomicInteger(1);

    // Calls by client-assigned id
    private Map<Integer, Call> calls = new ConcurrentHashMap<>();
    // Calls by server-assigned id
    private Map<Integer, Call> confirmedCalls = new ConcurrentHashMap<>();

    public WebSocketClient(ServerURL serverURL, WebSocketClientCallback callback) {
        this.host = serverURL.getHost();
        this.port = serverURL.getPort();
        this.tls = serverURL.isTLS();
        this.context = serverURL.getContext();
        this.callback = callback;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public void setConnectionTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean isAllowCompression() {
        return allowCompression;
    }

    public void setAllowCompression(boolean allowCompression) {
        this.allowCompression = allowCompression;
    }

    /**
     * Enables logging of all inbound and outbound messages on the request logging level.
     * <p>
     * By default set to {@link Level#FINEST}
     */
    public void setMessageLogging(Level level) {
        messageLogging = level;
    }

    public ChannelFuture connect(String authorization) throws SSLException, GeneralSecurityException {
        callback.connecting();
        return createBootstrap(authorization);
    }

    private ChannelFuture createBootstrap(String authorization) throws SSLException, GeneralSecurityException {
        HttpHeaders header = new DefaultHttpHeaders();
        if (userAgent != null) {
            header.add(HttpHeaderNames.USER_AGENT, userAgent);
        }

        if (authorization != null) {
            header.add(HttpHeaderNames.AUTHORIZATION, authorization);
        }
        URI uri;
        try {
            if (context == null) {
                uri = new URI(String.format("%s://%s:%s/api/websocket", (tls ? "wss" : "ws"), host, port));
            } else {
                uri = new URI(String.format("%s://%s:%s/%s/api/websocket", (tls ? "wss" : "ws"), host, port, context));
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri,
                WebSocketVersion.V13,
                "protobuf",
                true,
                header,
                maxFramePayloadLength);
        WebSocketClientHandler webSocketHandler = new WebSocketClientHandler(handshaker, this, callback);

        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, tcpKeepAlive);

        if (timeoutMs != null) {
            bootstrap = bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs);
        }
        SslContext sslCtx = tls ? getSslContext() : null;

        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                if (sslCtx != null) {
                    p.addLast(sslCtx.newHandler(ch.alloc()));
                }
                p.addLast(new HttpClientCodec());
                p.addLast(new HttpObjectAggregator(8192));
                if (allowCompression) {
                    p.addLast(WebSocketClientCompressionHandler.INSTANCE);
                }
                p.addLast(webSocketHandler);
            }
        });

        log.info("WebSocket client connecting");
        try {
            nettyChannel = bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();
        } catch (Exception e) {
            callback.connectionFailed(e);
        }

        // Finish handshake, this may still catch something like a 401
        return webSocketHandler.handshakeFuture();
    }

    /**
     * Initiates a new call. This does not yet communicate to Yamcs. Use the returned observer to send one or more
     * messages.
     */
    public <T extends Message> Observer<T> call(String type, DataObserver<? extends Message> observer) {
        Call call = new Call(type, observer);
        calls.put(call.correlationId, call);

        return new Observer<>() {

            @Override
            public void next(T message) {
                try {
                    call.write(message);
                } catch (IOException e) {
                    observer.completeExceptionally(e);
                }
            }

            @Override
            public void completeExceptionally(Throwable t) {
                observer.completeExceptionally(t);
            }

            @Override
            public void complete() {
                try {
                    cancelCall(call.callId);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }

    public void cancelCall(int callId) throws IOException {
        Call call = confirmedCalls.remove(callId);
        if (call != null) {
            calls.remove(call.correlationId);
        }
        writeMessage(ClientMessage.newBuilder()
                .setType("cancel")
                .setOptions(Any.pack(CancelOptions.newBuilder().setCall(callId).build()))
                .build());
    }

    public void disconnect() {
        log.info("WebSocket client sending close");
        nettyChannel.writeAndFlush(new CloseWebSocketFrame());

        // WebSocketClientHandler will close the channel when the server
        // responds to the CloseWebSocketFrame
        nettyChannel.closeFuture().awaitUninterruptibly();
    }

    private void writeMessage(Message message) throws IOException {
        if (log.isLoggable(messageLogging)) {
            log.log(messageLogging, ">>> " + message);
        }
        if (nettyChannel == null) {
            throw new IllegalStateException("Not connected");
        }
        ByteBuf buf = nettyChannel.alloc().buffer();
        try (ByteBufOutputStream bout = new ByteBufOutputStream(buf)) {
            message.writeTo(bout);
        }
        nettyChannel.writeAndFlush(new BinaryWebSocketFrame(buf));
    }

    /**
     * Enable/disable the TCP Keep-Alive on websocket sockets. By default it is disabled. It has to be enabled before
     * the connection is established.
     * 
     * @param enableTcpKeepAlive
     *            if true the TCP SO_KEEPALIVE option is set
     */
    public void enableTcpKeepAlive(boolean enableTcpKeepAlive) {
        tcpKeepAlive = enableTcpKeepAlive;
    }

    void completeAll() {
        calls.values().forEach(call -> call.serverObserver.complete());
        calls.clear();
        confirmedCalls.clear();
    }

    /**
     * @return the Future which is notified when the executor has been terminated.
     */
    public Future<?> shutdown() {
        return group.shutdownGracefully(0, 5, TimeUnit.SECONDS);
    }

    public boolean isConnected() {
        return nettyChannel != null && nettyChannel.isOpen();
    }

    private SslContext getSslContext() throws GeneralSecurityException, SSLException {
        if (insecureTls) {
            return SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        if (caKeyStore != null) {
            tmf.init(caKeyStore);
        } // else the default trustStore configured with -Djavax.net.ssl.trustStore is used

        return SslContextBuilder.forClient().trustManager(tmf).build();
    }

    /**
     * In case of https connections, this file contains the CA certificates that are used to verify server certificate
     * 
     * @param caCertFile
     */
    public void setCaCertFile(String caCertFile) throws IOException, GeneralSecurityException {
        caKeyStore = CertUtil.loadCertFile(caCertFile);
    }

    public boolean isInsecureTls() {
        return insecureTls;
    }

    /**
     * if true and https connections are used, do not verify server certificate
     * 
     * @param insecureTls
     */
    public void setInsecureTls(boolean insecureTls) {
        this.insecureTls = insecureTls;
    }

    public int getMaxFramePayloadLength() {
        return maxFramePayloadLength;
    }

    public void setMaxFramePayloadLength(int maxFramePayloadLength) {
        this.maxFramePayloadLength = maxFramePayloadLength;
    }

    void handleReply(ServerMessage message) throws InvalidProtocolBufferException {
        if (log.isLoggable(messageLogging)) {
            log.log(messageLogging, "<<< " + message);
        }
        Reply reply = message.getData().unpack(Reply.class);
        Call call = calls.get(reply.getReplyTo());
        if (call != null) {
            if (!reply.hasException()) {
                confirmedCalls.put(message.getCall(), call);
                call.assignCallId(message.getCall());
            } else {
                ExceptionMessage err = reply.getException();
                log.warning(String.format("Server error: %s: %s", err.getType(), err.getMsg()));
                ExceptionData excData = new ExceptionData(err.getType(), err.getMsg(), err.getDetail());
                call.serverObserver.completeExceptionally(new ClientException(excData));
            }
        } else {
            log.warning("Received a reply for an unknown call: " + reply);
        }
    }

    public void handleMessage(ServerMessage message) throws InvalidProtocolBufferException {
        if (log.isLoggable(messageLogging)) {
            log.log(messageLogging, "<<< " + message);
        }
        Call call = confirmedCalls.get(message.getCall());
        if (call != null) {
            call.serverObserver.unpackNext(message.getData());
        } else if (log.isLoggable(Level.FINER)) {
            // Usually just means that there was just a message underway while
            // the call was in the process of being cancelled.
            log.finer("Received a message for an unknown call: " + message);
        }
    }

    private class Call {

        final String type;
        final int correlationId = idSequence.getAndIncrement();
        final DataObserver<? extends Message> serverObserver;

        boolean first = true;
        int callId;
        CountDownLatch callIdLatch = new CountDownLatch(1);

        Call(String type, DataObserver<? extends Message> serverObserver) {
            this.type = type;
            this.serverObserver = serverObserver;
        }

        void write(Message data) throws IOException {
            if (first) {
                ClientMessage clientMessage = ClientMessage.newBuilder()
                        .setType(type)
                        .setId(correlationId)
                        .setOptions(Any.pack(data))
                        .build();
                writeMessage(clientMessage);
                first = false;
            } else {
                try {
                    callIdLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                ClientMessage clientMessage = ClientMessage.newBuilder()
                        .setType(type)
                        .setCall(callId)
                        .setOptions(Any.pack(data))
                        .build();
                writeMessage(clientMessage);
            }
        }

        void assignCallId(int callId) {
            this.callId = callId;
            serverObserver.confirm();
            callIdLatch.countDown();
        }
    }
}
