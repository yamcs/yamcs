package org.yamcs.web;

import java.io.File;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.net.ssl.SSLException;

import org.yamcs.YConfiguration;
import org.yamcs.api.AbstractYamcsService;
import org.yamcs.api.InitException;
import org.yamcs.api.Spec;
import org.yamcs.api.Spec.OptionType;
import org.yamcs.web.rest.Router;
import org.yamcs.web.websocket.ConnectedWebSocketClient;
import org.yamcs.web.websocket.WebSocketResource;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
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
public class HttpServer extends AbstractYamcsService {

    private EventLoopGroup bossGroup;
    private Router apiRouter;

    private int port;
    private int tlsPort;
    private String contextPath;
    private boolean zeroCopyEnabled;
    private List<String> staticRoots = new ArrayList<>(2);
    ThreadPoolExecutor executor;
    String tlsCert;
    String tlsKey;

    // Cross-origin Resource Sharing (CORS) enables use of the REST API in non-official client web applications
    private CorsConfig corsConfig;

    private Set<Function<ConnectedWebSocketClient, ? extends WebSocketResource>> webSocketExtensions = new HashSet<>();
    private GpbExtensionRegistry gpbExtensionRegistry = new GpbExtensionRegistry();

    // Extra handlers at root level. Wrapped in a Supplier because
    // we want to give the possiblity to make request-scoped instances
    private Map<String, Supplier<Handler>> extraHandlers = new HashMap<>();

    @Override
    public Spec getSpec() {
        Spec gpbSpec = new Spec();
        gpbSpec.addOption("class", OptionType.STRING).withRequired(true);
        gpbSpec.addOption("field", OptionType.STRING).withRequired(true);

        Spec corsSpec = new Spec();
        corsSpec.addOption("allowOrigin", OptionType.STRING).withRequired(true);
        corsSpec.addOption("allowCredentials", OptionType.BOOLEAN).withRequired(true);

        Spec websiteSpec = new Spec();
        websiteSpec.addOption("tag", OptionType.STRING);

        Spec lohiSpec = new Spec();
        lohiSpec.addOption("low", OptionType.INTEGER).withDefault(32 * 1024);
        lohiSpec.addOption("high", OptionType.INTEGER).withDefault(64 * 1024);

        Spec websocketSpec = new Spec();
        websocketSpec.addOption("writeBufferWaterMark", OptionType.MAP).withSpec(lohiSpec).withApplySpecDefaults(true);
        websocketSpec.addOption("connectionCloseNumDroppedMsg", OptionType.INTEGER).withDefault(5);
        websocketSpec.addOption("maxFrameLength", OptionType.INTEGER).withDefault(65535);

        Spec spec = new Spec();
        spec.addOption("port", OptionType.INTEGER);
        spec.addOption("tlsPort", OptionType.INTEGER);
        spec.addOption("tlsCert", OptionType.STRING);
        spec.addOption("tlsKey", OptionType.STRING);
        spec.addOption("contextPath", OptionType.STRING).withDefault("" /* NOT null */);
        spec.addOption("zeroCopyEnabled", OptionType.BOOLEAN).withDefault(true);
        spec.addOption("webRoot", OptionType.STRING)
                .withDeprecationMessage("This property is automatically set by the yamcs-web jar/plugin.");
        spec.addOption("gpbExtensions", OptionType.LIST).withElementType(OptionType.MAP).withSpec(gpbSpec);
        spec.addOption("cors", OptionType.MAP).withSpec(corsSpec);
        spec.addOption("website", OptionType.MAP).withSpec(websiteSpec);
        spec.addOption("webSocket", OptionType.MAP).withSpec(websocketSpec).withApplySpecDefaults(true);

        spec.requireOneOf("port", "tlsPort");
        spec.requireTogether("tlsPort", "tlsCert", "tlsKey");
        return spec;
    }

    @Override
    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        super.init(yamcsInstance, config);

        port = config.getInt("port", -1);
        tlsPort = config.getInt("tlsPort", -1);

        if (tlsPort != -1) {
            tlsCert = config.getString("tlsCert");
            tlsKey = config.getString("tlsKey");
        }
        contextPath = config.getString("contextPath");
        if (!contextPath.isEmpty()) {
            if (!contextPath.startsWith("/")) {
                throw new InitException("contextPath must start with a slash token");
            }
            if (contextPath.endsWith("/")) {
                throw new InitException("contextPath may not end with a slash token");
            }
        }

        zeroCopyEnabled = config.getBoolean("zeroCopyEnabled");

        // this is used to execute the routes marked as offThread
        ThreadFactory tf = new ThreadFactoryBuilder().setNameFormat("YamcsHttpExecutor-%d").setDaemon(false).build();
        executor = new ThreadPoolExecutor(0, 2 * Runtime.getRuntime().availableProcessors(), 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), tf);
        apiRouter = new Router(executor, contextPath);

        if (config.containsKey("gpbExtensions")) {
            List<Map<String, Object>> extensionsConf = config.getList("gpbExtensions");
            try {
                for (Map<String, Object> conf : extensionsConf) {
                    String className = YConfiguration.getString(conf, "class");
                    String fieldName = YConfiguration.getString(conf, "field");
                    Class<?> extensionClass = Class.forName(className);
                    Field field = extensionClass.getField(fieldName);
                    gpbExtensionRegistry.installExtension(extensionClass, field);
                }
            } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
                throw new InitException("Could not load GPB extensions", e);
            }
        }

        if (config.containsKey("cors")) {
            YConfiguration ycors = config.getConfig("cors");
            String[] origins = ycors.getString("allowOrigin").split(",");
            CorsConfigBuilder corsb = null;
            if (origins.length == 1) {
                corsb = CorsConfigBuilder.forOrigin(origins[0]);
            } else {
                corsb = CorsConfigBuilder.forOrigins(origins);
            }
            if (ycors.getBoolean("allowCredentials")) {
                corsb.allowCredentials();
            }

            corsb.allowedRequestMethods(HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH, HttpMethod.PUT,
                    HttpMethod.DELETE);
            corsb.allowedRequestHeaders(HttpHeaderNames.CONTENT_TYPE, HttpHeaderNames.ACCEPT,
                    HttpHeaderNames.AUTHORIZATION, HttpHeaderNames.ORIGIN);
            corsConfig = corsb.build();
        }
    }

    public void addStaticRoot(Path staticRoot) {
        staticRoots.add(staticRoot.toString());
    }

    public void addHandler(String pathSegment, Supplier<Handler> handlerSupplier) {
        extraHandlers.put(pathSegment, handlerSupplier);
    }

    public void addApiHandler(RouteHandler routeHandler) {
        apiRouter.registerRouteHandler(routeHandler);
    }

    @Deprecated
    public void addRouteHandler(RouteHandler routeHandler) {
        addApiHandler(routeHandler);
    }

    public void addApiHandler(String yamcsInstance, RouteHandler routeHandler) {
        apiRouter.registerRouteHandler(yamcsInstance, routeHandler);
    }

    @Deprecated
    public void addRouteHandler(String yamcsInstance, RouteHandler routeHandler) {
        addApiHandler(yamcsInstance, routeHandler);
    }

    @Override
    protected void doStart() {
        try {
            startServer();
            notifyStarted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            notifyFailed(e);
        } catch (Exception e) {
            notifyFailed(e);
        }
    }

    public void startServer() throws InterruptedException, SSLException, CertificateException {
        StaticFileHandler.init(staticRoots, zeroCopyEnabled);
        bossGroup = new NioEventLoopGroup(1);

        // Note that by default (i.e. with nThreads = 0), Netty will limit the number
        // of worker threads to 2*number of CPU cores
        EventLoopGroup workerGroup = new NioEventLoopGroup(0,
                new ThreadPerTaskExecutor(new DefaultThreadFactory("YamcsHttpServer")));

        if (port != -1) {
            createAndBindBootstrap(workerGroup, null, port);
        }
        if (tlsPort != -1) {
            SslContext sslCtx = SslContextBuilder.forServer(new File(tlsCert), new File(tlsKey)).build();
            createAndBindBootstrap(workerGroup, sslCtx, tlsPort);
        }

        try {
            if (port != -1) {
                log.info("Web address: http://{}:{}/", InetAddress.getLocalHost().getHostName(), port);
            }
            if (tlsPort != -1) {
                log.info("Web TLS address: https://{}:{}/", InetAddress.getLocalHost().getHostName(), tlsPort);
            }
        } catch (UnknownHostException e) {
            if (port != -1) {
                log.info("Web address: http://localhost:{}/", port);
            }
            if (tlsPort != -1) {
                log.info("Web TLS address: https://localhost:{}/", tlsPort);
            }
        }
    }

    private void createAndBindBootstrap(EventLoopGroup workerGroup, SslContext sslCtx, int port)
            throws InterruptedException {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(HttpServer.class, LogLevel.DEBUG))
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(new HttpServerChannelInitializer(this, sslCtx, apiRouter));

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(port)).sync();
    }

    public Future<?> stopServer() {
        return bossGroup.shutdownGracefully();
    }

    Handler createHandler(String pathSegment) {
        Supplier<Handler> supplier = extraHandlers.get(pathSegment);
        return supplier != null ? supplier.get() : null;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void addWebSocketExtension(Function<ConnectedWebSocketClient, ? extends WebSocketResource> extension) {
        webSocketExtensions.add(extension);
    }

    public Set<Function<ConnectedWebSocketClient, ? extends WebSocketResource>> getWebSocketExtensions() {
        return webSocketExtensions;
    }

    public GpbExtensionRegistry getGpbExtensionRegistry() {
        return gpbExtensionRegistry;
    }

    public CorsConfig getCorsConfig() {
        return corsConfig;
    }

    @Override
    protected void doStop() {
        stopServer();
        notifyStopped();
    }
}
