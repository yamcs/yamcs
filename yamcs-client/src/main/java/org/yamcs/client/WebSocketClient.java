package org.yamcs.client;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.protobuf.ConnectionInfo;
import org.yamcs.protobuf.WebSocketServerMessage.WebSocketExceptionData;
import org.yamcs.protobuf.WebSocketServerMessage.WebSocketReplyData;

import io.netty.bootstrap.Bootstrap;
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
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.concurrent.Future;

/**
 * Netty-implementation of a Yamcs web socket client
 */
public class WebSocketClient {

    private static final Logger log = Logger.getLogger(WebSocketClient.class.getName());
    private static final String SUBPROTOCOL_JSON = "json";
    private static final String SUBPROTOCOL_PROTOBUF = "protobuf";

    private WebSocketClientCallback callback;

    private EventLoopGroup group = new NioEventLoopGroup(1);
    private Channel nettyChannel;
    private String userAgent;
    private Integer timeoutMs;
    private AtomicBoolean enableReconnection = new AtomicBoolean(true);
    private AtomicInteger idSequence = new AtomicInteger(1);
    private YamcsConnectionProperties yprops;
    private String accessToken;
    final boolean useProtobuf = true;

    private ConnectionInfo connectionInfo;

    private boolean tcpKeepAlive = false;
    private boolean insecureTls;
    KeyStore caKeyStore;

    // if reconnection is enabled, how often to attempt to reconnect in case of failure
    long reconnectionInterval = 1000;

    // Keeps track of sent subscriptions, so that we can do a resend when we get
    // an InvalidException on some of them :-(
    private Map<Integer, RequestResponsePair> requestResponsePairBySeqId = new ConcurrentHashMap<>();

    private int maxFramePayloadLength = 65536;

    public int getMaxFramePayloadLength() {
        return maxFramePayloadLength;
    }

    public void setMaxFramePayloadLength(int maxFramePayloadLength) {
        this.maxFramePayloadLength = maxFramePayloadLength;
    }

    public WebSocketClient(WebSocketClientCallback callback) {
        this(null, callback);
    }

    public WebSocketClient(YamcsConnectionProperties yprops, WebSocketClientCallback callback) {
        this.yprops = yprops;
        this.callback = callback;
    }

    public WebSocketClient(YamcsConnectionProperties yprops) {
        this.yprops = yprops;
    }

    public void setConnectionProperties(YamcsConnectionProperties yprops) {
        this.yprops = yprops;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public void setConnectionTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * enable or disable reconnection in case of failure to connect or if the client is disconnected.
     * 
     * @param enableReconnection
     */
    public void enableReconnection(boolean enableReconnection) {
        this.enableReconnection.set(enableReconnection);
    }

    public ChannelFuture connect(String accessToken) throws SSLException, GeneralSecurityException {
        callback.connecting();
        this.accessToken = accessToken;
        return createBootstrap(accessToken);
    }

    public String getAccessToken() {
        return accessToken;
    }

    /**
     * set the reconnection interval in milliseconds.
     * 
     * This value is used when the connection fails and after the client is disconnected. Make sure to use the
     * {@link #enableReconnection(boolean)} to enable the recconnection.
     * 
     * @param reconnectionIntervalMillisec
     */
    public void setReconnectionInterval(long reconnectionIntervalMillisec) {
        this.reconnectionInterval = reconnectionIntervalMillisec;
    }

    private ChannelFuture createBootstrap(String accessToken) throws SSLException, GeneralSecurityException {
        HttpHeaders header = new DefaultHttpHeaders();
        if (userAgent != null) {
            header.add(HttpHeaderNames.USER_AGENT, userAgent);
        }

        if (accessToken != null) {
            String authorization = "Bearer " + accessToken;
            header.add(HttpHeaderNames.AUTHORIZATION, authorization);
        }
        String subprotocol = SUBPROTOCOL_JSON;
        if (useProtobuf) {
            header.add(HttpHeaderNames.ACCEPT, "application/protobuf");
            subprotocol = SUBPROTOCOL_PROTOBUF;
        }
        URI uri = yprops.webSocketURI();
        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13,
                subprotocol, false, header, maxFramePayloadLength);
        WebSocketClientHandler webSocketHandler = new WebSocketClientHandler(handshaker, this, callback);

        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, tcpKeepAlive);

        if (timeoutMs != null) {
            bootstrap = bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs);
        }
        SslContext sslCtx = yprops.isTls() ? getSslContext() : null;

        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                if (sslCtx != null) {
                    p.addLast(sslCtx.newHandler(ch.alloc()));
                }
                p.addLast(new HttpClientCodec(),
                        new HttpObjectAggregator(8192),
                        // new WebSocketClientCompressionHandler(),
                        webSocketHandler);
            }
        });

        log.info("WebSocket Client connecting");
        try {
            nettyChannel = bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel();
        } catch (Exception e) {
            callback.connectionFailed(e);
            if (enableReconnection.get()) {
                log.info("Attempting reconnect..");
                callback.connecting();
                group.schedule(() -> createBootstrap(accessToken), reconnectionInterval, TimeUnit.MILLISECONDS);
            }
        }

        // Finish handshake, this may still catch something like a 401
        return webSocketHandler.handshakeFuture();
    }

    /**
     * Performs the request in a different thread
     * 
     * @param request
     * @return future that completes when the request is answered
     */
    public CompletableFuture<WebSocketReplyData> sendRequest(WebSocketRequest request) {
        CompletableFuture<WebSocketReplyData> cf = new CompletableFuture<>();
        WebSocketResponseHandler wsr = new WebSocketResponseHandler() {
            @Override
            public void onException(WebSocketExceptionData e) {
                cf.completeExceptionally(new WebSocketExecutionException(e));
            }

            @Override
            public void onCompletion(WebSocketReplyData reply) {
                cf.complete(reply);
            }
        };
        group.execute(() -> doSendRequest(request, wsr));
        return cf;
    }

    public void sendRequest(WebSocketRequest request, WebSocketResponseHandler responseHandler) {
        group.execute(() -> doSendRequest(request, responseHandler));
    }

    /**
     * Really does send the request upstream
     */
    private void doSendRequest(WebSocketRequest request, WebSocketResponseHandler responseHandler) {
        int id = idSequence.incrementAndGet();
        requestResponsePairBySeqId.put(id, new RequestResponsePair(request, responseHandler));
        log.fine("Sending request " + request);
        nettyChannel.writeAndFlush(request.toWebSocketFrame(id));
    }

    RequestResponsePair getRequestResponsePair(int seqId) {
        return requestResponsePairBySeqId.get(seqId);
    }

    RequestResponsePair removeUpstreamRequest(int seqId) {
        return requestResponsePairBySeqId.remove(seqId);
    }

    boolean isReconnectionEnabled() {
        return enableReconnection.get();
    }

    public boolean isUseProtobuf() {
        return useProtobuf;
    }

    public void disconnect() {
        enableReconnection.set(false);
        log.info("WebSocket client sending close");
        nettyChannel.writeAndFlush(new CloseWebSocketFrame());

        // WebSocketClientHandler will close the channel when the server
        // responds to the CloseWebSocketFrame
        nettyChannel.closeFuture().awaitUninterruptibly();
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

    /**
     * @return the Future which is notified when the executor has been terminated.
     */
    public Future<?> shutdown() {
        return group.shutdownGracefully(0, 5, TimeUnit.SECONDS);
    }

    static class RequestResponsePair {
        WebSocketRequest request;
        WebSocketResponseHandler responseHandler;

        RequestResponsePair(WebSocketRequest request, WebSocketResponseHandler responseHandler) {
            this.request = request;
            this.responseHandler = responseHandler;
        }
    }

    public boolean isConnected() {
        return nettyChannel.isOpen();
    }

    public ConnectionInfo getConnectionInfo() {
        return connectionInfo;
    }

    void setConnectionInfo(ConnectionInfo connectionInfo) {
        this.connectionInfo = connectionInfo;
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
}
