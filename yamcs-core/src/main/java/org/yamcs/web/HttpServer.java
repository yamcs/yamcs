package org.yamcs.web;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.web.WebConfig.GpbExtension;
import org.yamcs.web.rest.Router;
import org.yamcs.web.websocket.WebSocketResourceProvider;

import com.google.common.util.concurrent.AbstractService;
import com.google.protobuf.Descriptors.FieldDescriptor.Type;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.GeneratedMessage.GeneratedExtension;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ThreadPerTaskExecutor;

/**
 * Server wide HTTP server based on Netty that provides a number of Yamcs web services:
 *
 * <ul>
 * <li>REST API
 * <li>WebSocket API
 * <li>Static file serving
 * </ul>
 */
public class HttpServer extends AbstractService {

    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);

    private EventLoopGroup bossGroup;
    private Router apiRouter = new Router();
    private List<WebSocketResourceProvider> webSocketResourceProviders = new CopyOnWriteArrayList<>();
    private WebConfig config;

    // Needed for JSON serialization of message extensions
    private ExtensionRegistry gpbExtensionRegistry = ExtensionRegistry.newInstance();

    public HttpServer() {
        this(WebConfig.getInstance());
    }

    public HttpServer(WebConfig config) {
        this.config = config;

        for (GpbExtension extension : config.getGpbExtensions()) {
            try {
                Class<?> extensionClazz = Class.forName(extension.clazz);
                Field field = extensionClazz.getField(extension.field);

                @SuppressWarnings("unchecked")
                GeneratedExtension<?, Type> genExtension = (GeneratedExtension<?, Type>) field.get(null);
                gpbExtensionRegistry.add(genExtension);
                log.info("Installing extension " + genExtension.getDescriptor().getFullName());
            } catch (IllegalAccessException e) {
                log.error("Could not load extension class", e);
                continue;
            } catch (ClassNotFoundException e) {
                log.error("Could not load extension class", e);
                continue;
            } catch (SecurityException e) {
                log.error("Could not load extension class", e);
                continue;
            } catch (NoSuchFieldException e) {
                log.error("Could not load extension class", e);
                continue;
            }
        }
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

        // Note that by default (i.e. with nThreads = 0), Netty will limit the number
        // of worker threads to 2*number of CPU cores
        EventLoopGroup workerGroup = new NioEventLoopGroup(0,
                new ThreadPerTaskExecutor(new DefaultThreadFactory("YamcsHttpServer")));

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

    public ExtensionRegistry getGpbExtensionRegistry() {
        return gpbExtensionRegistry;
    }

    @Override
    protected void doStop() {
        stopServer();
        notifyStopped();
    }
}
