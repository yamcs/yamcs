package org.yamcs.http;

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.yamcs.http.api.ActivitiesApi;
import org.yamcs.http.api.AlarmsApi;
import org.yamcs.http.api.AuditApi;
import org.yamcs.http.api.BucketsApi;
import org.yamcs.http.api.ClearanceApi;
import org.yamcs.http.api.CommandsApi;
import org.yamcs.http.api.Cop1Api;
import org.yamcs.http.api.DatabaseApi;
import org.yamcs.http.api.EventsApi;
import org.yamcs.http.api.FileTransferApi;
import org.yamcs.http.api.IamApi;
import org.yamcs.http.api.IndexesApi;
import org.yamcs.http.api.InstancesApi;
import org.yamcs.http.api.LinksApi;
import org.yamcs.http.api.MdbApi;
import org.yamcs.http.api.MdbOverrideApi;
import org.yamcs.http.api.PacketsApi;
import org.yamcs.http.api.ParameterArchiveApi;
import org.yamcs.http.api.ParameterListsApi;
import org.yamcs.http.api.ParameterValuesApi;
import org.yamcs.http.api.ProcessingApi;
import org.yamcs.http.api.QueuesApi;
import org.yamcs.http.api.ReplicationApi;
import org.yamcs.http.api.RocksDbApi;
import org.yamcs.http.api.ServerApi;
import org.yamcs.http.api.ServicesApi;
import org.yamcs.http.api.SessionsApi;
import org.yamcs.http.api.StreamArchiveApi;
import org.yamcs.http.api.TableApi;
import org.yamcs.http.api.TimeApi;
import org.yamcs.http.api.TimeCorrelationApi;
import org.yamcs.http.api.TimelineApi;
import org.yamcs.http.audit.AuditLog;
import org.yamcs.http.auth.AuthHandler;
import org.yamcs.http.auth.TokenStore;
import org.yamcs.protobuf.CancelOptions;
import org.yamcs.protobuf.Reply;
import org.yamcs.utils.ExceptionUtil;

import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ServiceManager;
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
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.ThreadPerTaskExecutor;

/**
 * Server-wide HTTP server based on Netty that provides a number of Yamcs web services:
 *
 * <ul>
 * <li>REST API
 * <li>WebSocket API
 * <li>Static file serving
 * </ul>
 */
public class HttpServer extends AbstractYamcsService {

    public static final HttpRoute WEBSOCKET_ROUTE = HttpRoute.newBuilder().setGet("/api/websocket").build();

    // Protobuf weirdness. When unspecified it defaults to "type.googleapis.com" ...
    public static final String TYPE_URL_PREFIX = "";

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelGroup clientChannels;
    private GlobalTrafficShapingHandler globalTrafficHandler;

    private List<Api<Context>> apis = new ArrayList<>();
    private List<Route> routes = new ArrayList<>();
    private List<Topic> topics = new ArrayList<>();

    private MetricRegistry metricRegistry = new MetricRegistry();

    private List<Binding> bindings = new ArrayList<>(2);

    private String contextPath;
    private boolean reverseLookup;
    private int nThreads;

    // Cross-origin Resource Sharing (CORS) enables use of the HTTP API in non-official client web applications
    private CorsConfig corsConfig;

    private ProtobufRegistry protobufRegistry = new ProtobufRegistry();
    private JsonFormat.Parser jsonParser;
    private JsonFormat.Printer jsonPrinter;

    // Services (may participate in start-stop events)
    private TokenStore tokenStore;
    private AuditLog auditLog;

    // Guava manager for sub-services
    private ServiceManager serviceManager;

    // Handlers at root level. Wrapped in a Supplier because
    // we want to give the possiblity to make request-scoped instances
    private Map<String, Supplier<HttpHandler>> httpHandlers = new HashMap<>();

    // Extra handlers at root level. Wrapped in a Supplier because
    // we want to give the possiblity to make request-scoped instances
    private Map<String, Supplier<Handler>> extraHandlers = new HashMap<>();

    @Override
    public Spec getSpec() {
        Spec corsSpec = new Spec();
        corsSpec.addOption("allowOrigin", OptionType.STRING).withRequired(true);
        corsSpec.addOption("allowCredentials", OptionType.BOOLEAN).withRequired(true);

        Spec websiteSpec = new Spec();
        websiteSpec.addOption("tag", OptionType.STRING);

        Spec lohiSpec = new Spec();
        lohiSpec.addOption("low", OptionType.INTEGER).withDefault(32 * 1024);
        lohiSpec.addOption("high", OptionType.INTEGER).withDefault(128 * 1024);

        Spec websocketSpec = new Spec();
        websocketSpec.addOption("writeBufferWaterMark", OptionType.MAP).withSpec(lohiSpec).withApplySpecDefaults(true);
        websocketSpec.addOption("maxFrameLength", OptionType.INTEGER).withDefault(65536);

        // Value in seconds. Both nginx and apache have a default timeout of 60 seconds before
        // they will close an idle WebSocket connection, therefore we choose a value well below that.
        websocketSpec.addOption("pingWhenIdleFor", OptionType.INTEGER).withDefault(40);

        Spec bindingSpec = new Spec();
        bindingSpec.addOption("address", OptionType.STRING);
        bindingSpec.addOption("port", OptionType.INTEGER).withRequired(true);
        bindingSpec.addOption("tlsCert", OptionType.LIST_OR_ELEMENT).withElementType(OptionType.STRING);
        bindingSpec.addOption("tlsKey", OptionType.STRING);
        bindingSpec.requireTogether("tlsCert", "tlsKey");

        Spec spec = new Spec();
        spec.addOption("address", OptionType.STRING);
        spec.addOption("port", OptionType.INTEGER).withDefault(8090);
        spec.addOption("tlsCert", OptionType.LIST_OR_ELEMENT).withElementType(OptionType.STRING);
        spec.addOption("tlsKey", OptionType.STRING);
        spec.addOption("contextPath", OptionType.STRING).withDefault("" /* NOT null */);
        spec.addOption("zeroCopyEnabled", OptionType.BOOLEAN).withDefault(true)
                .withDeprecationMessage("This optimization is automatically enabled where possible");
        spec.addOption("maxInitialLineLength", OptionType.INTEGER).withDefault(8192);
        spec.addOption("maxHeaderSize", OptionType.INTEGER).withDefault(8192);
        spec.addOption("maxContentLength", OptionType.INTEGER).withDefault(65536);
        spec.addOption("maxPageSize", OptionType.INTEGER).withDefault(1000);
        spec.addOption("cors", OptionType.MAP).withSpec(corsSpec);
        spec.addOption("webSocket", OptionType.MAP).withSpec(websocketSpec).withApplySpecDefaults(true);
        spec.addOption("bindings", OptionType.LIST)
                .withElementType(OptionType.MAP)
                .withSpec(bindingSpec);
        spec.addOption("nThreads", OptionType.INTEGER).withDefault(0);
        spec.addOption("reverseLookup", OptionType.BOOLEAN).withDefault(false);

        // When using multiple bindings, best to avoid confusion and disable the top-level properties
        spec.mutuallyExclusive("address", "bindings");
        spec.mutuallyExclusive("port", "bindings");
        spec.mutuallyExclusive("tlsCert", "bindings");
        spec.mutuallyExclusive("tlsKey", "bindings");

        spec.requireTogether("tlsCert", "tlsKey");
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);

        tokenStore = new TokenStore();
        auditLog = new AuditLog();

        tokenStore.init(this);
        auditLog.init(this);

        clientChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

        if (config.containsKey("bindings")) {
            for (YConfiguration bindingConfig : config.getConfigList("bindings")) {
                try {
                    Binding binding = Binding.fromConfig(bindingConfig);
                    bindings.add(binding);
                } catch (UnknownHostException e) {
                    throw new InitException("Cannot determine IP address for binding " + bindingConfig, e);
                }
            }
        } else {
            try {
                Binding binding = Binding.fromConfig(config);
                bindings.add(binding);
            } catch (UnknownHostException e) {
                throw new InitException("Cannot determine IP address for binding " + config, e);
            }
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

        reverseLookup = config.getBoolean("reverseLookup");

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
            corsb.allowedRequestMethods(
                    HttpMethod.GET,
                    HttpMethod.POST,
                    HttpMethod.PATCH,
                    HttpMethod.PUT,
                    HttpMethod.DELETE);
            corsb.allowedRequestHeaders(
                    HttpHeaderNames.CONTENT_TYPE,
                    HttpHeaderNames.ACCEPT,
                    HttpHeaderNames.AUTHORIZATION,
                    HttpHeaderNames.ORIGIN);
            corsConfig = corsb.build();
        }
        nThreads = config.getInt("nThreads");

        addApi(new ActivitiesApi());
        addApi(new AlarmsApi(auditLog));
        addApi(new AuditApi(auditLog));
        addApi(new BucketsApi());
        addApi(new FileTransferApi(auditLog));
        addApi(new ClearanceApi(auditLog));
        addApi(new CommandsApi());
        addApi(new Cop1Api());
        addApi(new DatabaseApi());
        addApi(new EventsApi());
        addApi(new IamApi(auditLog, tokenStore));
        addApi(new IndexesApi());
        addApi(new InstancesApi());
        addApi(new LinksApi(auditLog));
        addApi(new MdbApi());
        addApi(new MdbOverrideApi());
        addApi(new PacketsApi());
        addApi(new ParameterArchiveApi());
        addApi(new ParameterListsApi());
        addApi(new ParameterValuesApi());
        addApi(new ProcessingApi());
        addApi(new QueuesApi(auditLog));
        addApi(new ReplicationApi());
        addApi(new RocksDbApi(auditLog));
        addApi(new ServerApi(this));
        addApi(new ServicesApi());
        addApi(new SessionsApi());
        addApi(new StreamArchiveApi());
        addApi(new TableApi());
        addApi(new TimeApi());
        addApi(new TimeCorrelationApi());
        addApi(new TimelineApi());

        var wellKnownHandler = new WellKnownHandler();
        addRoute(".well-known", () -> wellKnownHandler);

        var authHandler = new AuthHandler(this);
        addRoute("auth", () -> authHandler);

        var faviconHandler = new FaviconHandler();
        for (var path : FaviconHandler.HANDLED_PATHS) {
            addRoute(path, () -> faviconHandler);
        }

        var robotsTxtHandler = new RobotsTxtHandler();
        for (var path : RobotsTxtHandler.HANDLED_PATHS) {
            addRoute(path, () -> robotsTxtHandler);
        }

        var apiHandler = new ApiHandler(this);
        addRoute("api", () -> apiHandler);

    }

    public void addRoute(String pathSegment, Supplier<HttpHandler> handler) {
        httpHandlers.put(pathSegment, handler);
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

    public void startServer() throws Exception {
        serviceManager = new ServiceManager(Arrays.asList(
                tokenStore, auditLog));
        serviceManager.startAsync().awaitHealthy(10, TimeUnit.SECONDS);

        bossGroup = new NioEventLoopGroup(1);

        // Note that by default (i.e. with nThreads = 0), Netty will limit the number
        // of worker threads to 2*number of CPU cores
        workerGroup = new NioEventLoopGroup(nThreads,
                new ThreadPerTaskExecutor(new DefaultThreadFactory("YamcsHttpServer")));

        // Measure global traffic, we also add a channel-specific measurer in channel-init.
        globalTrafficHandler = new GlobalTrafficShapingHandler(workerGroup, 5000);

        for (var binding : bindings) {
            createAndBindBootstrap(workerGroup, binding, globalTrafficHandler);
            log.debug("Serving from {}{}", binding, contextPath);
        }
    }

    private void createAndBindBootstrap(EventLoopGroup workerGroup, Binding binding,
            GlobalTrafficShapingHandler globalTrafficHandler)
            throws InterruptedException, SSLException, IOException {
        SslContext sslContext = null;
        if (binding.isTLS()) {
            sslContext = binding.createSslContext();
        }

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(HttpServer.class, LogLevel.DEBUG))
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(new HttpServerChannelInitializer(this, sslContext, globalTrafficHandler));

        // Bind and start to accept incoming connections.
        InetAddress address = binding.getAddress();
        int port = binding.getPort();
        if (address == null) {
            bootstrap.bind(new InetSocketAddress(port)).sync();
        } else {
            bootstrap.bind(new InetSocketAddress(address, port)).sync();
        }
    }

    HttpHandler createHttpHandler(String pathSegment) {
        Supplier<HttpHandler> supplier = httpHandlers.get(pathSegment);
        return supplier != null ? supplier.get() : null;
    }

    Handler createHandler(String pathSegment) {
        Supplier<Handler> supplier = extraHandlers.get(pathSegment);
        return supplier != null ? supplier.get() : null;
    }

    public TokenStore getTokenStore() {
        return tokenStore;
    }

    public AuditLog getAuditLog() {
        return auditLog;
    }

    public List<Binding> getBindings() {
        return bindings;
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

    public ProtobufRegistry getProtobufRegistry() {
        return protobufRegistry;
    }

    public GlobalTrafficShapingHandler getGlobalTrafficShapingHandler() {
        return globalTrafficHandler;
    }

    public JsonFormat.Parser getJsonParser() {
        return jsonParser;
    }

    public JsonFormat.Printer getJsonPrinter() {
        return jsonPrinter;
    }

    public boolean getReverseLookup() {
        return reverseLookup;
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
        globalTrafficHandler.release();
        var closers = listeningDecorator(Executors.newCachedThreadPool());
        var future1 = closers.submit(() -> {
            return workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).get();
        });
        var future2 = closers.submit(() -> {
            return bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).get();
        });
        var future3 = closers.submit(() -> {
            serviceManager.stopAsync();
            serviceManager.awaitStopped(5, TimeUnit.SECONDS);
            return true; // Force use of Callable interface, instead of Runnable
        });
        closers.shutdown();
        Futures.addCallback(Futures.allAsList(future1, future2, future3), new FutureCallback<>() {
            @Override
            public void onSuccess(List<Object> result) {
                notifyStopped();
            }

            @Override
            public void onFailure(Throwable t) {
                notifyFailed(ExceptionUtil.unwind(t));
            }
        }, MoreExecutors.directExecutor());
    }
}
