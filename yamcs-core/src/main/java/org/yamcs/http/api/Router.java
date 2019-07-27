package org.yamcs.http.api;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Plugin;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.YamcsVersion;
import org.yamcs.http.HttpException;
import org.yamcs.http.HttpRequestHandler;
import org.yamcs.http.HttpUtils;
import org.yamcs.http.MethodNotAllowedException;
import org.yamcs.http.RouteHandler;
import org.yamcs.http.api.archive.ArchiveAlarmRestHandler;
import org.yamcs.http.api.archive.ArchiveCommandRestHandler;
import org.yamcs.http.api.archive.ArchiveDownloadRestHandler;
import org.yamcs.http.api.archive.ArchiveEventRestHandler;
import org.yamcs.http.api.archive.ArchiveIndexDownloadsRestHandler;
import org.yamcs.http.api.archive.ArchiveIndexRestHandler;
import org.yamcs.http.api.archive.ArchivePacketRestHandler;
import org.yamcs.http.api.archive.ArchiveParameterRestHandler;
import org.yamcs.http.api.archive.ArchiveSqlRestHandler;
import org.yamcs.http.api.archive.ArchiveStreamRestHandler;
import org.yamcs.http.api.archive.ArchiveTableRestHandler;
import org.yamcs.http.api.archive.ArchiveTagRestHandler;
import org.yamcs.http.api.archive.RocksDbMaintenanceRestHandler;
import org.yamcs.http.api.mdb.MDBAlgorithmRestHandler;
import org.yamcs.http.api.mdb.MDBCommandRestHandler;
import org.yamcs.http.api.mdb.MDBContainerRestHandler;
import org.yamcs.http.api.mdb.MDBParameterRestHandler;
import org.yamcs.http.api.mdb.MDBParameterTypeRestHandler;
import org.yamcs.http.api.mdb.MDBRestHandler;
import org.yamcs.http.api.mdb.MDBSpaceSystemRestHandler;
import org.yamcs.http.api.processor.ProcessorCommandQueueRestHandler;
import org.yamcs.http.api.processor.ProcessorCommandRestHandler;
import org.yamcs.http.api.processor.ProcessorParameterRestHandler;
import org.yamcs.http.api.processor.ProcessorRestHandler;
import org.yamcs.parameterarchive.ParameterArchiveMaintenanceRestHandler;
import org.yamcs.protobuf.Rest.GetApiOverviewResponse;
import org.yamcs.protobuf.Rest.GetApiOverviewResponse.PluginInfo;
import org.yamcs.protobuf.Rest.GetApiOverviewResponse.RouteInfo;
import org.yamcs.security.User;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;

/**
 * Matches a request uri to a registered route handler. Stops on the first match.
 * <p>
 * The Router itself has the same granularity as HttpServer: one instance only.
 * <p>
 * When matching a route, priority is first given to built-in routes, only if none match the first matching
 * instance-specific dynamic route is matched. Dynamic routes often mention ':instance' in their url, which will be
 * expanded upon registration into the actual yamcs instance.
 */
@Sharable
public class Router extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Pattern ROUTE_PATTERN = Pattern.compile("(\\/)?:(\\w+)([\\?\\*])?");
    private static final Logger log = LoggerFactory.getLogger(Router.class);

    public final static int MAX_BODY_SIZE = 65536;
    public static final AttributeKey<RouteMatch> CTX_ROUTE_MATCH = AttributeKey.valueOf("routeMatch");
    private static final FullHttpResponse CONTINUE = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
            HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);

    private String contextPath;

    // Order, because patterns are matched top-down in insertion order
    private List<RouteElement> defaultRoutes = new ArrayList<>();
    private List<RouteElement> dynamicRoutes = new ArrayList<>();

    private boolean logSlowRequests = true;
    int SLOW_REQUEST_TIME = 20;// seconds; requests that execute more than this are logged
    ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
    private final ExecutorService offThreadExecutor;

    public Router(ExecutorService executor, String contextPath) {
        this.offThreadExecutor = executor;
        this.contextPath = contextPath;
        registerRouteHandler(new CfdpRestHandler());
        registerRouteHandler(new ClientRestHandler());
        registerRouteHandler(new InstanceRestHandler());
        registerRouteHandler(new LinkRestHandler());
        registerRouteHandler(new ServiceRestHandler());
        registerRouteHandler(new TemplateRestHandler());
        registerRouteHandler(new UserRestHandler());

        registerRouteHandler(new ArchiveAlarmRestHandler());
        registerRouteHandler(new ArchiveCommandRestHandler());
        registerRouteHandler(new ArchiveDownloadRestHandler());
        registerRouteHandler(new ArchiveEventRestHandler());
        registerRouteHandler(new ArchiveIndexDownloadsRestHandler());
        registerRouteHandler(new ArchiveIndexRestHandler());
        registerRouteHandler(new ArchivePacketRestHandler());
        registerRouteHandler(new ArchiveParameterRestHandler());
        registerRouteHandler(new ArchiveStreamRestHandler());
        registerRouteHandler(new ArchiveSqlRestHandler());
        registerRouteHandler(new ArchiveTableRestHandler());
        registerRouteHandler(new ArchiveTagRestHandler());
        registerRouteHandler(new BucketRestHandler());
        registerRouteHandler(new ParameterArchiveMaintenanceRestHandler());
        registerRouteHandler(new RocksDbMaintenanceRestHandler());

        registerRouteHandler(new ProcessorRestHandler());
        registerRouteHandler(new ProcessorParameterRestHandler());
        registerRouteHandler(new ProcessorCommandRestHandler());
        registerRouteHandler(new ProcessorCommandQueueRestHandler());

        registerRouteHandler(new MDBRestHandler());
        registerRouteHandler(new MDBSpaceSystemRestHandler());
        registerRouteHandler(new MDBParameterRestHandler());
        registerRouteHandler(new MDBParameterTypeRestHandler());
        registerRouteHandler(new MDBContainerRestHandler());
        registerRouteHandler(new MDBCommandRestHandler());
        registerRouteHandler(new MDBAlgorithmRestHandler());

        registerRouteHandler(new OverviewRouteHandler());
    }

    public void registerRouteHandler(RouteHandler routeHandler) {
        registerRouteHandler(null, routeHandler);
    }

    // Using method handles for better invoke performance
    public void registerRouteHandler(String yamcsInstance, RouteHandler routeHandler) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Method[] declaredMethods = routeHandler.getClass().getDeclaredMethods();

        // Temporary structure used to sort before map insertion
        List<RouteConfig> routeConfigs = new ArrayList<>();
        try {
            for (int i = 0; i < declaredMethods.length; i++) {
                Method reflectedMethod = declaredMethods[i];
                if (reflectedMethod.isAnnotationPresent(Route.class)
                        || reflectedMethod.isAnnotationPresent(Routes.class)) {
                    MethodHandle handle = lookup.unreflect(reflectedMethod);

                    Route[] anns = reflectedMethod.getDeclaredAnnotationsByType(Route.class);
                    for (Route ann : anns) {
                        for (String m : ann.method()) {
                            HttpMethod httpMethod = HttpMethod.valueOf(m);
                            routeConfigs.add(new RouteConfig(routeHandler, ann.path(), ann.priority(), ann.dataLoad(),
                                    ann.offThread(), ann.maxBodySize(), httpMethod, handle));
                        }
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(
                    "Could not access @Route annotated method in " + routeHandler.getClass());
        }

        // Sort in a way that increases chances of a good URI match
        // 1. @Route(priority=true) first
        // 2. Descending on path length
        // 3. Actual path contents (should not matter too much)
        Collections.sort(routeConfigs);

        List<RouteElement> targetRoutes = (yamcsInstance == null) ? defaultRoutes : dynamicRoutes;

        for (RouteConfig routeConfig : routeConfigs) {
            String routeString = routeConfig.originalPath;
            if (yamcsInstance != null) { // Expand :instance upon registration (only for dynamic routes)
                if (!routeString.contains(":instance")) {
                    log.warn("Dynamically added route {} {} is instance-specific, yet does not "
                            + "contain ':instance' in its url. Routing of incoming requests "
                            + "will be ambiguous.", routeConfig.httpMethod, routeConfig.originalPath);
                }
                routeString = routeString.replace(":instance", yamcsInstance);
            }
            Pattern pattern = toPattern(routeString);
            Map<HttpMethod, RouteConfig> configByMethod = createAndGet(targetRoutes, pattern).configByMethod;
            configByMethod.put(routeConfig.httpMethod, routeConfig);
        }
    }

    private RouteElement createAndGet(List<RouteElement> routes, Pattern pattern) {
        for (RouteElement re : routes) {
            if (re.pattern.pattern().equals(pattern.pattern())) {
                return re;
            }
        }
        RouteElement re = new RouteElement(pattern);
        routes.add(re);
        return re;
    }

    /**
     * At this point we do not have the full request (only the header) so we have to configure the pipeline either for
     * receiving the full request or with route specific pipeline for receiving (large amounts of) data in case of
     * dataLoad routes.
     * 
     * @param ctx
     * @param req
     * @param uri
     * @return true if the request has been scheduled and false if the request is invalid or there was another error
     */
    public boolean scheduleExecution(ChannelHandlerContext ctx, HttpRequest req, String uri) {
        try {
            RouteMatch match = matchURI(req.method(), uri);
            if (match == null) {
                log.info("No route matching URI: '{}'", req.uri());
                HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.NOT_FOUND);
                return false;
            }
            ctx.channel().attr(CTX_ROUTE_MATCH).set(match);
            RouteConfig rc = match.getRouteConfig();
            if (rc.isDataLoad()) {
                try {
                    RouteHandler target = match.routeConfig.routeHandler;
                    match.routeConfig.handle.invoke(target, ctx, req, match);
                    if (HttpUtil.is100ContinueExpected(req)) {
                        ctx.writeAndFlush(CONTINUE.retainedDuplicate());
                    }
                } catch (HttpException e) {
                    log.warn("Error invoking data load handler on URI '{}': {}", req.uri(), e.getMessage());
                    HttpRequestHandler.sendPlainTextError(ctx, req, e.getStatus(), e.getMessage());
                } catch (Throwable t) {
                    log.error("Error invoking data load handler on URI: '{}'", req.uri(), t);
                    HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.BAD_REQUEST);
                }
            } else {
                ctx.pipeline().addLast(HttpRequestHandler.HANDLER_NAME_COMPRESSOR, new HttpContentCompressor());

                // this will cause the channelRead0 to be called as soon as the request is complete
                // it will also reject requests whose body is greater than the MAX_BODY_SIZE)
                ctx.pipeline().addLast(new HttpObjectAggregator(rc.maxBodySize()));
                ctx.pipeline().addLast(this);
                ctx.fireChannelRead(req);
            }
            return true;
        } catch (MethodNotAllowedException e) {
            log.info("Method {} not allowed for URI: '{}'", req.method(), req.uri());
            HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.BAD_REQUEST);
        }
        return false;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        User user = ctx.channel().attr(HttpRequestHandler.CTX_USER).get();
        RouteMatch match = ctx.channel().attr(CTX_ROUTE_MATCH).get();
        String uri = HttpUtils.getUriWithoutContext(req, contextPath);
        QueryStringDecoder qsDecoder = new QueryStringDecoder(uri);
        RestRequest restReq = new RestRequest(ctx, req, qsDecoder, user);
        restReq.setRouteMatch(match);
        log.debug("R{}: Handling REST Request {} {}", restReq.getRequestId(), req.method(), req.uri());
        if (match.routeConfig.offThread) {
            restReq.getRequestContent().retain();
            offThreadExecutor.execute(() -> {
                dispatch(restReq, match);
                restReq.getRequestContent().release();
            });
        } else {
            dispatch(restReq, match);
        }
    }

    public RouteMatch matchURI(HttpMethod method, String uri) throws MethodNotAllowedException {
        Set<HttpMethod> allowedMethods = null;
        for (RouteElement re : defaultRoutes) {
            Matcher matcher = re.pattern.matcher(uri);
            if (matcher.matches()) {
                Map<HttpMethod, RouteConfig> byMethod = re.configByMethod;
                if (byMethod.containsKey(method)) {
                    return new RouteMatch(matcher, byMethod.get(method));
                } else {
                    if (allowedMethods == null) {
                        allowedMethods = new HashSet<>(4);
                    }
                    allowedMethods.addAll(byMethod.keySet());
                }
            }
        }

        for (RouteElement re : dynamicRoutes) {
            Matcher matcher = re.pattern.matcher(uri);
            if (matcher.matches()) {
                Map<HttpMethod, RouteConfig> byMethod = re.configByMethod;
                if (byMethod.containsKey(method)) {
                    return new RouteMatch(matcher, byMethod.get(method));
                } else {
                    if (allowedMethods == null) {
                        allowedMethods = new HashSet<>(4);
                    }
                    allowedMethods.addAll(byMethod.keySet());
                }
            }
        }

        if (allowedMethods != null) { // One or more rules matched, but with wrong method
            throw new MethodNotAllowedException(method, uri, allowedMethods);
        } else { // No rule was matched
            return null;
        }
    }

    protected void dispatch(RestRequest req, RouteMatch match) {
        boolean offThread = match.routeConfig.offThread;
        ScheduledFuture<?> twoSecWarn = null;
        if (!offThread) {
            twoSecWarn = timer.schedule(() -> {
                log.error("R{} blocking the netty thread for 2 seconds. uri: {}", req.getRequestId(),
                        req.getHttpRequest().uri());
            }, 2, TimeUnit.SECONDS);
        }

        // the handlers will send themselves the response unless they throw an exception, case which is handled in the
        // catch below.
        try {
            RouteHandler target = match.routeConfig.routeHandler;
            match.routeConfig.handle.invoke(target, req);
            req.getCompletableFuture().whenComplete((channelFuture, e) -> {
                if (e != null) {
                    log.debug("R{}: REST request execution finished with error: {}, transferred bytes: {}",
                            req.getRequestId(), e.getMessage(), req.getTransferredSize());
                } else {
                    log.debug("R{}: REST request execution finished successfully, transferred bytes: {}",
                            req.getRequestId(), req.getTransferredSize());
                }
            });
        } catch (Throwable t) {
            req.getCompletableFuture().completeExceptionally(t);
            handleException(req, t);
        }
        if (twoSecWarn != null) {
            twoSecWarn.cancel(true);
        }

        CompletableFuture<Void> cf = req.getCompletableFuture();
        if (logSlowRequests) {
            int numSec = offThread ? 120 : 20;
            timer.schedule(() -> {
                if (!cf.isDone()) {
                    log.warn("R{} executing for more than 20 seconds. uri: {}", req.getRequestId(),
                            req.getHttpRequest().uri());
                }
            }, numSec, TimeUnit.SECONDS);
        }
    }

    private void handleException(RestRequest req, Throwable t) {
        if (t instanceof HttpException) {
            HttpException e = (HttpException) t;
            if (e.isServerError()) {
                log.error("R{}: Responding '{}': {}", req.getRequestId(), e.getStatus(), e.getMessage(), e);
                RestHandler.sendRestError(req, e.getStatus(), e);
            } else {
                log.warn("R{}: Responding '{}': {}", req.getRequestId(), e.getStatus(), e.getMessage());
                RestHandler.sendRestError(req, e.getStatus(), e);
            }
        } else {
            log.error("R{}: Responding '{}'", req.getRequestId(), HttpResponseStatus.INTERNAL_SERVER_ERROR, t);
            RestHandler.sendRestError(req, HttpResponseStatus.INTERNAL_SERVER_ERROR, t);
        }
    }

    /*
     * Pattern matching loosely inspired from angular and express.js
     */
    private Pattern toPattern(String route) {
        Matcher matcher = ROUTE_PATTERN.matcher(route);
        StringBuffer buf = new StringBuffer("^");
        while (matcher.find()) {
            boolean star = ("*".equals(matcher.group(3)));
            boolean optional = ("?".equals(matcher.group(3)));
            String slash = (matcher.group(1) != null) ? matcher.group(1) : "";
            StringBuffer replacement = new StringBuffer();
            if (optional) {
                replacement.append("(?:");
                replacement.append(slash);
                replacement.append("(?<").append(matcher.group(2)).append(">");
                replacement.append(star ? ".+?" : "[^/]+");
                replacement.append(")?)?");
            } else {
                replacement.append(slash);
                replacement.append("(?<").append(matcher.group(2)).append(">");
                replacement.append(star ? ".+?" : "[^/]+");
                replacement.append(")");
            }

            matcher.appendReplacement(buf, replacement.toString());
        }
        matcher.appendTail(buf);
        return Pattern.compile(buf.append("/?$").toString());
    }

    /**
     * Struct containing all non-path route configuration
     */
    public static final class RouteConfig implements Comparable<RouteConfig> {
        final RouteHandler routeHandler;
        final String originalPath;
        final boolean priority;
        final HttpMethod httpMethod;
        final MethodHandle handle;
        final boolean dataLoad;
        final int maxBodySize;
        final boolean offThread;

        RouteConfig(RouteHandler routeHandler, String originalPath, boolean priority, boolean dataLoad,
                boolean offThread, int maxBodySize, HttpMethod httpMethod, MethodHandle handle) {
            this.routeHandler = routeHandler;
            this.originalPath = originalPath;
            this.priority = priority;
            this.httpMethod = httpMethod;
            this.handle = handle;
            this.dataLoad = dataLoad;
            this.maxBodySize = maxBodySize;
            this.offThread = offThread;
        }

        @Override
        public int compareTo(RouteConfig o) {
            int priorityCompare = Boolean.compare(priority, o.priority);
            if (priorityCompare != 0) {
                return -priorityCompare;
            } else {
                int pathLengthCompare = Integer.compare(originalPath.length(), o.originalPath.length());
                if (pathLengthCompare != 0) {
                    return -pathLengthCompare;
                } else {
                    return originalPath.compareTo(o.originalPath);
                }
            }
        }

        public boolean isDataLoad() {
            return dataLoad;
        }

        public int maxBodySize() {
            return maxBodySize;
        }
    }

    /**
     * Represents a matched route pattern
     */
    public static final class RouteMatch {
        final Matcher regexMatch;
        final RouteConfig routeConfig;

        RouteMatch(Matcher regexMatch, RouteConfig routeConfig) {
            this.regexMatch = regexMatch;
            this.routeConfig = routeConfig;
        }

        public RouteConfig getRouteConfig() {
            return routeConfig;
        }

        public String getRouteParam(String name) {
            return regexMatch.group(name);
        }
    }

    /**
     * stores the matching patterns together with the config per HttpMethod
     */
    public static final class RouteElement {
        final Pattern pattern;
        final Map<HttpMethod, RouteConfig> configByMethod = new LinkedHashMap<>();

        RouteElement(Pattern p) {
            this.pattern = p;
        }
    }

    /**
     * 'Documents' all registered resources, and provides some general server information.
     */
    private final class OverviewRouteHandler extends RestHandler {
        @Route(path = "/api", method = "GET")
        public void getApiOverview(RestRequest req) throws HttpException {
            GetApiOverviewResponse.Builder responseb = GetApiOverviewResponse.newBuilder();
            responseb.setYamcsVersion(YamcsVersion.VERSION);
            responseb.setRevision(YamcsVersion.REVISION);
            responseb.setServerId(YamcsServer.getServer().getServerId());

            List<Plugin> plugins = new ArrayList<>(YamcsServer.getServer().getPlugins());
            plugins.sort((p1, p2) -> p1.getName().compareTo(p2.getName()));
            for (Plugin plugin : plugins) {
                PluginInfo.Builder pluginb = PluginInfo.newBuilder()
                        .setName(plugin.getName());
                if (plugin.getVersion() != null) {
                    pluginb.setVersion(plugin.getVersion());
                }
                if (plugin.getVendor() != null) {
                    pluginb.setVendor(plugin.getVendor());
                }
                if (plugin.getDescription() != null) {
                    pluginb.setDescription(plugin.getDescription());
                }
                responseb.addPlugin(pluginb);
            }

            // Property to be interpreted at client's leisure.
            // Concept of defaultInstance could be moved into YamcsServer
            // at some point, but there's for now unsufficient support.
            // (would need websocket adjustments, which are now
            // instance-specific).
            YConfiguration yconf = YamcsServer.getServer().getConfig();
            if (yconf.containsKey("defaultInstance")) {
                responseb.setDefaultYamcsInstance(yconf.getString("defaultInstance"));
            } else {
                Set<YamcsServerInstance> instances = YamcsServer.getInstances();
                if (!instances.isEmpty()) {
                    YamcsServerInstance anyInstance = instances.iterator().next();
                    responseb.setDefaultYamcsInstance(anyInstance.getName());
                }
            }

            // Aggregate to unique urls, and keep insertion order
            Map<String, RouteInfo.Builder> builders = new LinkedHashMap<>();
            for (RouteElement re : defaultRoutes) {
                re.configByMethod.values().forEach(v -> {
                    String path = contextPath + v.originalPath;
                    RouteInfo.Builder builder = builders.get(path);
                    if (builder == null) {
                        builder = RouteInfo.newBuilder();
                        builders.put(path, builder);
                    }
                    builder.setUrl(path).addMethod(v.httpMethod.toString());
                });
            }
            builders.values().forEach(b -> responseb.addRoute(b));
            completeOK(req, responseb.build());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Will close channel due to exception", cause);
        ctx.close();
    }
}
