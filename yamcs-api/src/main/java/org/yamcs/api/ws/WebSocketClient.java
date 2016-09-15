package org.yamcs.api.ws;

import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.MediaType;
import org.yamcs.api.YamcsConnectionProperties;
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

    private final WebSocketClientCallback callback;

    private EventLoopGroup group = new NioEventLoopGroup(1);
    private Channel nettyChannel;
    private String userAgent;
    private Integer timeoutMs=null;
    private AtomicBoolean enableReconnection = new AtomicBoolean(true);
    private AtomicInteger seqId = new AtomicInteger(1);
    YamcsConnectionProperties yprops;
    final boolean useProtobuf = true;

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

    public void setConnectionProperties(YamcsConnectionProperties yprops) {
        this.yprops=yprops;
    }
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public void setConnectionTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public ChannelFuture connect() {
        return connect(false);
    }

    public ChannelFuture connect(boolean enableReconnection) {
        this.enableReconnection.set(enableReconnection);
        return createBootstrap();
    }

    private ChannelFuture createBootstrap() {
        HttpHeaders header = new DefaultHttpHeaders();
        if (userAgent != null) {
            header.add(HttpHeaders.Names.USER_AGENT, userAgent);
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
                    header.add(HttpHeaders.Names.AUTHORIZATION, authorization);
                }
            } else {
                throw new RuntimeException("authentication token of type "+authToken.getClass()+" not supported");
            }
        }
        if(useProtobuf) {
            header.add(HttpHeaders.Names.ACCEPT, MediaType.PROTOBUF);
        }
        URI uri = yprops.webSocketURI();

        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13,
                null, false, header);
        WebSocketClientHandler webSocketHandler = new WebSocketClientHandler(handshaker, this, callback);

        Bootstrap bootstrap = new Bootstrap()
        .group(group)
        .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        .channel(NioSocketChannel.class);

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
                        group.schedule(() -> createBootstrap(), 1L, TimeUnit.SECONDS);
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
     */
    public void sendRequest(WebSocketRequest request) {
        group.execute(() -> doSendRequest(request, null));
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

    void forgetUpstreamRequest(int seqId) {
        requestResponsePairBySeqId.remove(seqId);
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
        YamcsConnectionProperties yprops = new YamcsConnectionProperties("localhost", 8090, "simulator");
        CountDownLatch latch = new CountDownLatch(1);
        WebSocketClient client = new WebSocketClient(yprops, new WebSocketClientCallback() {
            @Override
            public void connected() {
                System.out.println("Connected..........");
                latch.countDown();
            }

            @Override
            public void connectionFailed(Throwable t) {
                System.out.println("failed.........."+t.getMessage());
            }

            @Override
            public void onMessage(WebSocketSubscriptionData data) {
                System.out.println("Got data " + data);
            }
        });

        client.connect();
        latch.await();

        client.sendRequest(new WebSocketRequest("time", "subscribe"));

        Thread.sleep(5000);
        client.shutdown();
    }
    public boolean isConnected() {
        return nettyChannel.isOpen();
    }
}
