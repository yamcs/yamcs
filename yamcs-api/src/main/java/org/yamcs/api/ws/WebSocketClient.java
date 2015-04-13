package org.yamcs.api.ws;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Yamcs.NamedObjectList;

/**
 * Netty-implementation of a Yamcs web socket client
 */

public class WebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(WebSocketClient.class);

    private WebSocketClientCallbackListener callback;
    private EventLoopGroup group = new NioEventLoopGroup();
    private URI uri;
    private Channel nettyChannel;
    private String userAgent;
    private AtomicBoolean connected = new AtomicBoolean(false);
    private AtomicBoolean enableReconnection = new AtomicBoolean(true);
    private AtomicInteger seqId = new AtomicInteger(1);

    // Stores ws subscriptions to be sent to the server once ws-connection is established
    private BlockingQueue<WebSocketRequest> pendingRequests = new LinkedBlockingQueue<>();

    // Sends outgoing subscriptions to the web socket
    // private ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

    // Keeps track of sent subscriptions, so that we can do a resend when we get
    // an InvalidException on some of them :-(
    private ConcurrentHashMap<Integer, WebSocketRequest> upstreamRequestBySeqId = new ConcurrentHashMap<>();

    // TODO we should actually wait for the ACK to arrive, before sending the next request
    public WebSocketClient(YamcsConnectionProperties yprops, WebSocketClientCallbackListener callback) {
	this.uri = yprops.webSocketURI();
	this.callback = callback;
	(new Thread(){
	    public void run(){
		try {
		    WebSocketRequest evt;
		    while ((evt = pendingRequests.take()) != null) {
			// We now have at least one event to handle
			Thread.sleep(500); // Wait for more events, before going into synchronized block
			synchronized (pendingRequests) {
			    while (pendingRequests.peek() != null
				    && evt.canMergeWith(pendingRequests.peek())) {
				WebSocketRequest otherEvt = pendingRequests.poll();
				evt = evt.mergeWith(otherEvt); // This is to counter bursts.
			    }
			}

			// Good, send the merged result
			doSendRequest(evt);
		    }
		} catch (InterruptedException e) {
		    log.error("OOPS, got interrupted", e);
		}
	    }
	}).start();
    }

    /**
     * Formatted as app/version. No spaces.
     */
    public void setUserAgent(String userAgent) {
	this.userAgent = userAgent;
    }

    public void connect() {
	enableReconnection.set(true);
	createBootstrap();
    }

    void setConnected(boolean connected) {
	this.connected.set(connected);
    }

    public boolean isConnected() {
	return connected.get();
    }

    private void createBootstrap() {
	HttpHeaders header = new DefaultHttpHeaders();
	if (userAgent != null) {
	    header.add(HttpHeaders.Names.USER_AGENT, userAgent);
	}

	WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
		uri, WebSocketVersion.V13, null, false, header);
	final WebSocketClientHandler webSocketHandler = new WebSocketClientHandler(handshaker, this, callback);

	Bootstrap bootstrap = new Bootstrap();
	bootstrap.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
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
	try {
	    ChannelFuture future = bootstrap.connect(uri.getHost(), uri.getPort()).addListener(new ChannelFutureListener() {
		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
		    if (!future.isSuccess()) {
			// Set-up reconnection attempts every second during initial set-up.
			log.info("reconnect..");
			//group.schedule(() -> createBootstrap(), 1L, TimeUnit.SECONDS);
		    }
		}
	    });

	    future.sync();
	    nettyChannel = future.sync().channel();
	} catch (InterruptedException e) {
	    System.out.println("interrupted while trying to connect");
	    e.printStackTrace();
	}
    }

    /**
     * Adds said event to the queue. As soon as the web socket is established, queue will be
     * iterated and if possible, similar events will be merged.
     */
    public void sendRequest(WebSocketRequest request) {
	// sync, because the consumer will try to merge multiple outgoing events of the same type
	// using multiple operations on the queue.
	synchronized (pendingRequests) {
	    pendingRequests.offer(request);
	}
    }

    /**
     * Really does send the request upstream
     */
    private void doSendRequest(WebSocketRequest request) {
	int id = seqId.incrementAndGet();
	upstreamRequestBySeqId.put(id, request);
	log.debug("Sending request {}", request);
	nettyChannel.writeAndFlush(request.toWebSocketFrame(id));
    }

    WebSocketRequest getUpstreamRequest(int seqId) {
	return upstreamRequestBySeqId.get(seqId);
    }

    void forgetUpstreamRquest(int seqId) {
	upstreamRequestBySeqId.remove(seqId);
    }

    boolean isReconnectionEnabled() {
	return enableReconnection.get();
    }

    public void disconnect() {
	if (connected.compareAndSet(true, false)) {
	    enableReconnection.set(false);
	    log.info("WebSocket Client sending close");
	    nettyChannel.writeAndFlush(new CloseWebSocketFrame());

	    // WebSocketClientHandler will close the channel when the server responds to the
	    // CloseWebSocketFrame
	    nettyChannel.closeFuture().awaitUninterruptibly();
	} else {
	    log.debug("Close requested, but connection was already closed");
	}
    }

    /**
     * @return the Future which is notified when the executor has been terminated.
     */
    public Future<?> shutdown() {
	// exec.shutdown();
	return group.shutdownGracefully();
    }
}
