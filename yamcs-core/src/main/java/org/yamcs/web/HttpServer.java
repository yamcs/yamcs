package org.yamcs.web;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsService;
import org.yamcs.web.rest.Router;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
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
public class HttpServer extends AbstractService implements YamcsService {

    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);

    private EventLoopGroup bossGroup;
    private Router apiRouter;

    private int port;
    private boolean zeroCopyEnabled;
    private List<String> webRoots = new ArrayList<>(2);
    ThreadPoolExecutor executor;

    // Cross-origin Resource Sharing (CORS) enables use of the REST API in non-official client web applications
    private CorsConfig corsConfig;

    private WebSocketConfig wsConfig;

    private GpbExtensionRegistry gpbExtensionRegistry = new GpbExtensionRegistry();

    public HttpServer() {
        this(Collections.emptyMap());
    }

    public HttpServer(Map<String, Object> args) {
        YConfiguration yconf = YConfiguration.getConfiguration("yamcs");
        if (yconf.containsKey("webConfig")) {
            log.warn("Deprecation: Define webConfig properties as args on the HttpServer");
            args = yconf.getMap("webConfig");
        }

        port = YConfiguration.getInt(args, "port", 8090);
        zeroCopyEnabled = YConfiguration.getBoolean(args, "zeroCopyEnabled", true);

        if (args.containsKey("webRoot")) {
            if (YConfiguration.isList(args, "webRoot")) {
                List<String> rootConf = YConfiguration.getList(args, "webRoot");
                webRoots.addAll(rootConf);
            } else {
                webRoots.add(YConfiguration.getString(args, "webRoot"));
            }
        }

        // this is used to execute the routes marked as offThread
        ThreadFactory tf = new ThreadFactoryBuilder().setNameFormat("YamcsHttpExecutor-%d").setDaemon(false).build();
        executor = new ThreadPoolExecutor(0, 2 * Runtime.getRuntime().availableProcessors(), 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), tf);
        apiRouter = new Router(executor);

        if (args.containsKey("gpbExtensions")) {
            List<Map<String, Object>> extensionsConf = YConfiguration.getList(args, "gpbExtensions");
            for (Map<String, Object> conf : extensionsConf) {
                String className = YConfiguration.getString(conf, "class");
                String fieldName = YConfiguration.getString(conf, "field");
                try {
                    Class<?> extensionClass = Class.forName(className);
                    Field field = extensionClass.getField(fieldName);
                    gpbExtensionRegistry.installExtension(extensionClass, field);
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

        if (args.containsKey("cors")) {
            Map<String, Object> ycors = YConfiguration.getMap(args, "cors");
            CorsConfigBuilder corsb = null;
            if (YConfiguration.isList(ycors, "allowOrigin")) {
                List<String> originConf = YConfiguration.getList(ycors, "allowOrigin");
                corsb = CorsConfigBuilder.forOrigins(originConf.toArray(new String[originConf.size()]));
            } else {
                corsb = CorsConfigBuilder.forOrigin(YConfiguration.getString(ycors, "allowOrigin"));
            }
            if (YConfiguration.getBoolean(ycors, "allowCredentials")) {
                corsb.allowCredentials();
            }

            corsb.allowedRequestMethods(HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH, HttpMethod.PUT,
                    HttpMethod.DELETE);
            corsb.allowedRequestHeaders(HttpHeaderNames.CONTENT_TYPE, HttpHeaderNames.ACCEPT,
                    HttpHeaderNames.AUTHORIZATION, HttpHeaderNames.ORIGIN);
            corsConfig = corsb.build();
        }

        wsConfig = new WebSocketConfig();
        if (args.containsKey("webSocket")) {
            Map<String, Object> wsArgs = YConfiguration.getMap(args, "webSocket");
            if (wsArgs.containsKey("writeBufferWaterMark")) {
                Map<String, Object> watermarkArgs = YConfiguration.getMap(wsArgs, "writeBufferWaterMark");
                int low = YConfiguration.getInt(watermarkArgs, "low");
                int high = YConfiguration.getInt(watermarkArgs, "high");
                wsConfig.setWriteBufferWaterMark(new WriteBufferWaterMark(low, high));
            }
            if (wsArgs.containsKey("connectionCloseNumDroppedMsg")) {
                int connectionCloseNumDroppedMsg = YConfiguration.getInt(wsArgs, "connectionCloseNumDroppedMsg");
                if (connectionCloseNumDroppedMsg < 1) {
                    throw new ConfigurationException(
                            "connectionCloseNumDroppedMsg has to be greater than 0. Provided value: "
                                    + connectionCloseNumDroppedMsg);
                }
                wsConfig.setConnectionCloseNumDroppedMsg(connectionCloseNumDroppedMsg);
            }
            if (wsArgs.containsKey("maxFrameLength")) {
                int maxFrameLength = YConfiguration.getInt(wsArgs, "maxFrameLength");
                wsConfig.setMaxFrameLength(maxFrameLength);
            }
        }
    }

    @Override
    protected void doStart() {
        try {
            startServer();
            notifyStarted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            notifyFailed(e);
        }
    }

    public void startServer() throws InterruptedException {
        StaticFileHandler.init(webRoots, zeroCopyEnabled);
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
                .childHandler(new HttpServerChannelInitializer(apiRouter, corsConfig, wsConfig));

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

    public void registerRouteHandler(String yamcsInstance, RouteHandler routeHandler) {
        apiRouter.registerRouteHandler(yamcsInstance, routeHandler);
    }

    public GpbExtensionRegistry getGpbExtensionRegistry() {
        return gpbExtensionRegistry;
    }

    @Override
    protected void doStop() {
        stopServer();
        notifyStopped();
    }
}
