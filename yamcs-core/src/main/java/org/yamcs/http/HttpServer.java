package org.yamcs.http;

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;

import java.io.File;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.net.ssl.SSLException;

import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.api.Api;
import org.yamcs.api.HttpRoute;
import org.yamcs.api.WebSocketTopic;
import org.yamcs.http.api.AlarmsApi;
import org.yamcs.http.api.BucketsApi;
import org.yamcs.http.api.CfdpApi;
import org.yamcs.http.api.ClearanceApi;
import org.yamcs.http.api.ClientsApi;
import org.yamcs.http.api.CommandHistoryApi;
import org.yamcs.http.api.Cop1Api;
import org.yamcs.http.api.EventsApi;
import org.yamcs.http.api.GeneralApi;
import org.yamcs.http.api.IamApi;
import org.yamcs.http.api.IndexApi;
import org.yamcs.http.api.ManagementApi;
import org.yamcs.http.api.MdbApi;
import org.yamcs.http.api.PacketsApi;
import org.yamcs.http.api.ParameterArchiveApi;
import org.yamcs.http.api.ProcessingApi;
import org.yamcs.http.api.QueueApi;
import org.yamcs.http.api.RocksDbApi;
import org.yamcs.http.api.StreamArchiveApi;
import org.yamcs.http.api.TableApi;
import org.yamcs.http.api.TagApi;
import org.yamcs.http.api.TimeApi;
import org.yamcs.http.auth.AuthHandler;
import org.yamcs.http.auth.TokenStore;
import org.yamcs.http.websocket.ConnectedWebSocketClient;
import org.yamcs.http.websocket.WebSocketResource;
import org.yamcs.protobuf.CancelOptions;
import org.yamcs.protobuf.Reply;
import org.yamcs.utils.ExceptionUtil;

import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.TypeRegistry;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
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
import io.netty.util.concurrent.GlobalEventExecutor;
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

    public static final HttpRoute WEBSOCKET_ROUTE = HttpRoute.newBuilder().setGet("/api/websocket").build();

    // Protobuf weirdness. When unspecified it default to "type.googleapis.com" ...
    public static final String TYPE_URL_PREFIX = "";

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelGroup clientChannels;

    private List<Api<Context>> apis = new ArrayList<>();
    private List<Route> routes = new ArrayList<>();
    private List<Topic> topics = new ArrayList<>();

    private MetricRegistry metricRegistry = new MetricRegistry();

    private int port;
    private int tlsPort;
    private String contextPath;
    private boolean zeroCopyEnabled;
    private List<String> staticRoots = new ArrayList<>(2);
    private String tlsCert;
    private String tlsKey;

    // Cross-origin Resource Sharing (CORS) enables use of the REST API in non-official client web applications
    private CorsConfig corsConfig;

    private Set<Function<ConnectedWebSocketClient, ? extends WebSocketResource>> webSocketExtensions = new HashSet<>();

    private ProtobufRegistry protobufRegistry = new ProtobufRegistry();
    private JsonFormat.Parser jsonParser;
    private JsonFormat.Printer jsonPrinter;

    private TokenStore tokenStore = new TokenStore();

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
        spec.addOption("gpbExtensions", OptionType.LIST).withElementType(OptionType.MAP).withSpec(gpbSpec);
        spec.addOption("cors", OptionType.MAP).withSpec(corsSpec);
        spec.addOption("webSocket", OptionType.MAP).withSpec(websocketSpec).withApplySpecDefaults(true);

        spec.requireOneOf("port", "tlsPort");
        spec.requireTogether("tlsPort", "tlsCert", "tlsKey");
        return spec;
    }

    @Override
    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        super.init(yamcsInstance, config);

        clientChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

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

        if (config.containsKey("gpbExtensions")) {
            List<Map<String, Object>> extensionsConf = config.getList("gpbExtensions");
            try {
                for (Map<String, Object> conf : extensionsConf) {
                    String className = YConfiguration.getString(conf, "class");
                    String fieldName = YConfiguration.getString(conf, "field");
                    Class<?> extensionClass = Class.forName(className);
                    Field field = extensionClass.getField(fieldName);
                    protobufRegistry.installExtension(extensionClass, field);
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

        addApi(new AlarmsApi());
        addApi(new BucketsApi());
        addApi(new CfdpApi());
        addApi(new ClearanceApi());
        addApi(new ClientsApi());
        addApi(new CommandHistoryApi());
        addApi(new Cop1Api());
        addApi(new GeneralApi(this));
        addApi(new EventsApi());
        addApi(new IamApi());
        addApi(new IndexApi());
        addApi(new ManagementApi());
        addApi(new MdbApi());
        addApi(new PacketsApi());
        addApi(new ParameterArchiveApi());
        addApi(new ProcessingApi());
        addApi(new QueueApi());
        addApi(new StreamArchiveApi());
        addApi(new RocksDbApi());
        addApi(new TableApi());
        addApi(new TagApi());
        addApi(new TimeApi());

        AuthHandler authHandler = new AuthHandler(tokenStore, contextPath);
        addHandler("auth", () -> authHandler);
    }

    public void addStaticRoot(Path staticRoot) {
        staticRoots.add(staticRoot.toString());
    }

    public void addHandler(String pathSegment, Supplier<Handler> handlerSupplier) {
        extraHandlers.put(pathSegment, handlerSupplier);
    }

    public void addApi(Api<Context> api) {
        apis.add(api);
        for (MethodDescriptor method : api.getDescriptorForType().getMethods()) {
            RpcDescriptor descriptor = protobufRegistry.getRpc(method.getFullName());
            if (descriptor == null) {
                throw new UnsupportedOperationException("Unable to find rpc definition: " + method.getFullName());
            }

            if (WEBSOCKET_ROUTE.equals(descriptor.getHttpRoute())) {
                topics.add(new Topic(api, descriptor.getWebSocketTopic(), descriptor));
                for (WebSocketTopic topic : descriptor.getAdditionalWebSocketTopics()) {
                    topics.add(new Topic(api, topic, descriptor));
                }
            } else {
                routes.add(new Route(api, descriptor.getHttpRoute(), descriptor, metricRegistry));
                for (HttpRoute route : descriptor.getAdditionalHttpRoutes()) {
                    routes.add(new Route(api, route, descriptor, metricRegistry));
                }
            }
        }

        // Regenerate JSON converters with type support (needed for the "Any" type)
        TypeRegistry.Builder typeRegistryb = TypeRegistry.newBuilder();
        typeRegistryb.add(CancelOptions.getDescriptor());
        typeRegistryb.add(Reply.getDescriptor());
        apis.forEach(a -> typeRegistryb.add(a.getDescriptorForType().getFile().getMessageTypes()));
        TypeRegistry typeRegistry = typeRegistryb.build();

        jsonParser = JsonFormat.parser().usingTypeRegistry(typeRegistry);
        jsonPrinter = JsonFormat.printer().usingTypeRegistry(typeRegistry);

        // Sort in a way that increases chances of a good URI match
        Collections.sort(routes);
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
        workerGroup = new NioEventLoopGroup(0,
                new ThreadPerTaskExecutor(new DefaultThreadFactory("YamcsHttpServer")));

        if (port != -1) {
            createAndBindBootstrap(workerGroup, null, port);
            log.debug("Serving http from {}", getHttpBaseUri());
        }
        if (tlsPort != -1) {
            SslContext sslCtx = SslContextBuilder.forServer(new File(tlsCert), new File(tlsKey)).build();
            createAndBindBootstrap(workerGroup, sslCtx, tlsPort);
            log.debug("Serving https from {}", getHttpsBaseUri());
        }
    }

    private void createAndBindBootstrap(EventLoopGroup workerGroup, SslContext sslCtx, int port)
            throws InterruptedException {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(HttpServer.class, LogLevel.DEBUG))
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(new HttpServerChannelInitializer(this, sslCtx));

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(port)).sync();
    }

    public boolean isHttpEnabled() {
        return port != -1;
    }

    /**
     * Returns a prettified rendering of the known absolute http address (including context root).
     */
    public String getHttpBaseUri() {
        if (!isHttpEnabled()) {
            return null;
        }

        StringBuilder b = new StringBuilder("http://");
        try {
            b.append(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            b.append("localhost");
        }
        if (port != 80) {
            b.append(":").append(port);
        }

        return b.append(contextPath).toString();
    }

    public boolean isHttpsEnabled() {
        return tlsPort != -1;
    }

    /**
     * Returns a prettified rendering of the known absolute https address (including context root).
     */
    public String getHttpsBaseUri() {
        if (!isHttpsEnabled()) {
            return null;
        }

        StringBuilder b = new StringBuilder("https://");
        try {
            b.append(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            b.append("localhost");
        }
        if (tlsPort != 443) {
            b.append(":").append(tlsPort);
        }

        return b.append(contextPath).toString();
    }

    Handler createHandler(String pathSegment) {
        Supplier<Handler> supplier = extraHandlers.get(pathSegment);
        return supplier != null ? supplier.get() : null;
    }

    public TokenStore getTokenStore() {
        return tokenStore;
    }

    public String getContextPath() {
        return contextPath;
    }

    public List<Route> getRoutes() {
        return routes;
    }

    public List<Topic> getTopics() {
        return topics;
    }

    public void addWebSocketExtension(Function<ConnectedWebSocketClient, ? extends WebSocketResource> extension) {
        webSocketExtensions.add(extension);
    }

    public Set<Function<ConnectedWebSocketClient, ? extends WebSocketResource>> getWebSocketExtensions() {
        return webSocketExtensions;
    }

    public ProtobufRegistry getProtobufRegistry() {
        return protobufRegistry;
    }

    public JsonFormat.Parser getJsonParser() {
        return jsonParser;
    }

    public JsonFormat.Printer getJsonPrinter() {
        return jsonPrinter;
    }

    public CorsConfig getCorsConfig() {
        return corsConfig;
    }

    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    void trackClientChannel(Channel channel) {
        clientChannels.add(channel);
    }

    public List<Channel> getClientChannels() {
        return new ArrayList<>(clientChannels);
    }

    public void closeChannel(String id) {
        clientChannels.close(ch -> ch.id().asShortText().equals(id));
    }

    @Override
    protected void doStop() {
        ListeningExecutorService closers = listeningDecorator(Executors.newCachedThreadPool());
        ListenableFuture<?> future1 = closers.submit(() -> {
            return workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).get();
        });
        ListenableFuture<?> future2 = closers.submit(() -> {
            return bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).get();
        });
        closers.shutdown();
        Futures.addCallback(Futures.allAsList(future1, future2), new FutureCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                notifyStopped();
            }

            @Override
            public void onFailure(Throwable t) {
                notifyFailed(ExceptionUtil.unwind(t));
            }
        });
    }
}
