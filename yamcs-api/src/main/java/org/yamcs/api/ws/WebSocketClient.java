package org.yamcs.api.ws;

import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.MediaType;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketExceptionData;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.UsernamePasswordToken;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
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
import io.netty.util.concurrent.Future;

/**
 * Netty-implementation of a Yamcs web socket client
 */
public class WebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(WebSocketClient.class);

    private WebSocketClientCallback callback;

    private EventLoopGroup group = new NioEventLoopGroup(1);
    private Channel nettyChannel;
    private String userAgent;
    private Integer timeoutMs=null;
    private AtomicBoolean enableReconnection = new AtomicBoolean(true);
    private AtomicInteger seqId = new AtomicInteger(1);
    YamcsConnectionProperties yprops;
    final boolean useProtobuf = true;

    private boolean tcpKeepAlive = false;
    
    //if reconnection is enabled, how often to attempt to reconnect in case of failure
    long reconnectionInterval = 1000;

    // Keeps track of sent subscriptions, so that we can do a resend when we get
    // an InvalidException on some of them :-(
    private Map<Integer, RequestResponsePair> requestResponsePairBySeqId = new ConcurrentHashMap<>();


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
        this.yprops=yprops;
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

    public ChannelFuture connect() {    
        return createBootstrap();
    }

    /**
     * set the reconnection interval in milliseconds.
     * 
     * This value is used when the connection fails and after the client is disconnected.
     * Make sure to use the {@link #enableReconnection(boolean)} to enable the recconnection. 
     * 
     * @param reconnectionIntervalMillisec
     */
    public void setReconnectionInterval(long reconnectionIntervalMillisec) {
        this.reconnectionInterval = reconnectionIntervalMillisec;
    }

    private ChannelFuture createBootstrap() {
        HttpHeaders header = new DefaultHttpHeaders();
        if (userAgent != null) {
            header.add(HttpHeaderNames.USER_AGENT, userAgent);
        }

        AuthenticationToken authToken = yprops.getAuthenticationToken();
        if(authToken!=null) {
            if(authToken instanceof UsernamePasswordToken) {
                String username = ((UsernamePasswordToken)authToken).getUsername();
                String password = ((UsernamePasswordToken)authToken).getPasswordS();
                if (username != null) {
                    String credentialsClear = username;
                    if (password != null)
                        credentialsClear += ":" + password;
                    String credentialsB64 = new String(Base64.getEncoder().encode(credentialsClear.getBytes()));
                    String authorization = "Basic " + credentialsB64;
                    header.add(HttpHeaderNames.AUTHORIZATION, authorization);
                }
            } else {
                throw new RuntimeException("authentication token of type "+authToken.getClass()+" not supported");
            }
        }
        if(useProtobuf) {
            header.add(HttpHeaderNames.ACCEPT, MediaType.PROTOBUF);
        }
        URI uri = yprops.webSocketURI();

        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13,
                null, false, header);
        WebSocketClientHandler webSocketHandler = new WebSocketClientHandler(handshaker, this, callback);

        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, tcpKeepAlive);

        if(timeoutMs!=null) {
            bootstrap = bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs);
        }
        
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(
                        new HttpClientCodec(),
                        new HttpObjectAggregator(8192),
                        // new WebSocketClientCompressionHandler(),
                        webSocketHandler);
            }
        });

        log.info("WebSocket Client connecting");
        ChannelFuture future = bootstrap.connect(uri.getHost(), uri.getPort());
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    nettyChannel = future.channel();
                } else {
                    callback.connectionFailed(future.cause());
                    if (enableReconnection.get()) {
                        // Set-up reconnection attempts every second
                        // during initial set-up.
                        log.info("reconnect..");
                        group.schedule(() -> createBootstrap(), reconnectionInterval, TimeUnit.MILLISECONDS);
                    }
                }
            }
        });

        return future;

    }

    /**
     * Performs the request in a different thread
     * 
     * @param request 
     * @return future that completes when the request is anwsered
     */
    public CompletableFuture<Void> sendRequest(WebSocketRequest request) {
        CompletableFuture<Void> cf = new CompletableFuture<Void>();
        WebSocketResponseHandler wsr = new WebSocketResponseHandler() {
            @Override
            public void onException(WebSocketExceptionData e) {
                cf.completeExceptionally(new WebSocketExecutionException(e));
            }

            @Override
            public void onCompletion() {
                cf.complete(null);

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
        int id = seqId.incrementAndGet();
        requestResponsePairBySeqId.put(id, new RequestResponsePair(request, responseHandler));
        log.debug("Sending request {}", request);
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
        log.info("WebSocket Client sending close");
        nettyChannel.writeAndFlush(new CloseWebSocketFrame());

        // WebSocketClientHandler will close the channel when the server
        // responds to the CloseWebSocketFrame
        nettyChannel.closeFuture().awaitUninterruptibly();
    }

    /**
     * Enable/disable the TCP Keep-Alive on websocket sockets. By default it is disabled. It has to be enabled before the connection is estabilished. 
     * @param enableTcpKeepAlive - if true the TCP SO_KEEPALIVE option is set 
     */
    public void enableTcpKeepAlive(boolean enableTcpKeepAlive) {
        tcpKeepAlive = enableTcpKeepAlive;
    }
    /**
     * @return the Future which is notified when the executor has been
     *         terminated.
     */
    public Future<?> shutdown() {
        return group.shutdownGracefully();
    }

    static class RequestResponsePair {
        WebSocketRequest request;
        WebSocketResponseHandler responseHandler;
        RequestResponsePair(WebSocketRequest request, WebSocketResponseHandler responseHandler) {
            this.request = request;
            this.responseHandler = responseHandler;
        }
    }

    public static void main(String... args) throws InterruptedException {
        YamcsConnectionProperties yprops = new YamcsConnectionProperties("aces-archive-ops.spaceapplications.com", 8090, "mwlgt-ops");
        WebSocketClient client = new WebSocketClient(yprops);
        client.enableTcpKeepAlive(true);
        
        client.setCallback(new WebSocketClientCallback() {
            @Override
            public void connected() {
                System.out.println("Connection succeeded.......... subscribing to time");
                client.sendRequest(new WebSocketRequest("time", "subscribe"));
            }

            @Override
            public void connectionFailed(Throwable t) {
                System.out.println("Connection failed.........."+t.getMessage());
            }

            @Override
            public void onMessage(WebSocketSubscriptionData data) {
                System.out.println("Got data " + data);
            }


            @Override
            public void disconnected() {
                System.out.println("Disconnected.....");
            }
        });


        client.enableReconnection(true);
        client.setReconnectionInterval(100);
        client.connect();


        Thread.sleep(500000);
        client.shutdown();
    }
    private void setCallback(WebSocketClientCallback webSocketClientCallback) {
        this.callback = webSocketClientCallback;

    }

    public boolean isConnected() {
        return nettyChannel.isOpen();
    }
    
   
}
