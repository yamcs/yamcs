package org.yamcs.web;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.web.rest.Router;
import org.yamcs.web.websocket.WebSocketResourceProvider;

import com.google.common.util.concurrent.AbstractService;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.Future;

/**
 * Server wide HTTP server based on Netty that provides a number of
 * Yamcs web services:
 *
 * <ul>
 *  <li>REST API
 *  <li>WebSocket API
 *  <li>Static file serving
 * </ul>
 */
public class HttpServer extends AbstractService {

    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);

    private EventLoopGroup bossGroup;
    private Router apiRouter = new Router();
    private List<WebSocketResourceProvider> webSocketResourceProviders = new CopyOnWriteArrayList<>();
    private WebConfig config;

    public HttpServer() {
        this(WebConfig.getInstance());
    }

    public HttpServer(WebConfig config) {
        this.config = config;
    }

    @Override
    protected void doStart() {
        try {
            startServer();
            notifyStarted();
        } catch (InterruptedException e) {
            notifyFailed(e);
        }
    }

    public void startServer() throws InterruptedException {
        StaticFileHandler.init();
        int port = config.getPort();
        bossGroup = new NioEventLoopGroup(1);

        //Note that while the thread pools created with this method are unbounded, netty will limit the number
        //of workers to 2*number of CPU cores
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .handler(new LoggingHandler(HttpServer.class, LogLevel.DEBUG))
        .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        .childHandler(new HttpServerChannelInitializer(apiRouter));

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(port)).sync();

        try {
            log.info("Web address: http://{}:{}/", InetAddress.getLocalHost().getHostName(), port);
        } catch (UnknownHostException e) {
            log.info("Web address: http://localhost:{}/", port);
        }
    }

    public Future<?> stopServer() {
        return bossGroup.shutdownGracefully();
    }

    public WebConfig getConfig() {
        return config;
    }

    public void registerRouteHandler(String yamcsInstance, RouteHandler routeHandler) {
        apiRouter.registerRouteHandler(yamcsInstance, routeHandler);
    }

    public void registerWebSocketRouteHandler(WebSocketResourceProvider provider) {
        webSocketResourceProviders.add(provider);
    }

    public List<WebSocketResourceProvider> getWebSocketResourceProviders() {
        return webSocketResourceProviders;
    }

    @Override
    protected void doStop() {
        stopServer();
        notifyStopped();
    }
}
