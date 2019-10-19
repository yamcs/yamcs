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

import org.yamcs.api.Api;
import org.yamcs.api.HttpRoute;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.HttpRequestHandler;
import org.yamcs.http.HttpUtils;
import org.yamcs.http.MethodNotAllowedException;
import org.yamcs.http.ProtobufRegistry;
import org.yamcs.http.RouteHandler;
import org.yamcs.http.RpcDescriptor;
import org.yamcs.http.api.archive.ArchiveCommandRestHandler;
import org.yamcs.http.api.archive.ArchiveDownloadRestHandler;
import org.yamcs.http.api.archive.ArchiveIndexDownloadsRestHandler;
import org.yamcs.http.api.archive.ArchiveTableRestHandler;
import org.yamcs.http.api.processor.ProcessorCommandQueueRestHandler;
import org.yamcs.http.api.processor.ProcessorRestHandler;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.RouteInfo;
import org.yamcs.security.User;

import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;

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
 * instance-specific dynamic route is matched. Dynamic routes often mention '{instance}' in their url, which will be
 * expanded upon registration into the actual yamcs instance.
 */
@Sharable
public class Router extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Pattern ROUTE_PATTERN = Pattern.compile("(\\/)?\\{(\\w+)(\\?|\\*|\\*\\*)?\\}");
    private static final Log log = new Log(Router.class);

    public static final int MAX_BODY_SIZE = 65536;
    public static final AttributeKey<RouteMatch> CTX_ROUTE_MATCH = AttributeKey.valueOf("routeMatch");
    private static final FullHttpResponse CONTINUE = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
            HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);

    private String contextPath;
    private ProtobufRegistry protobufRegistry;

    private List<Api<Context>> apis = new ArrayList<>();

    // Order, because patterns are matched top-down in insertion order
    private List<RouteElement> defaultRoutes = new ArrayList<>();
    private List<RouteElement> dynamicRoutes = new ArrayList<>();

    private boolean logSlowRequests = true;
    int SLOW_REQUEST_TIME = 20;// seconds; requests that execute more than this are logged
    ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
    private final ExecutorService offThreadExecutor;

    public Router(ExecutorService executor, String contextPath, ProtobufRegistry protobufRegistry) {
        this.offThreadExecutor = executor;
        this.contextPath = contextPath;
        this.protobufRegistry = protobufRegistry;

        addApi(new BucketsApi());
        addApi(new CfdpApi());
        addApi(new GeneralApi(this));
        addApi(new ExportApi());
        addApi(new IamApi());
        addApi(new ManagementApi());
        addApi(new MdbApi());
        addApi(new ParameterArchiveApi());
        addApi(new ProcessingApi());
        addApi(new StreamArchiveApi());
        addApi(new RocksDbApi());
        addApi(new TableApi());
        addApi(new TagApi());
        addApi(new Cop1Api());

        // Not (yet) converted to Protobuf-style API
        registerRouteHandler(new MdbRestHandler());
        registerRouteHandler(new ArchiveTableRestHandler());
        registerRouteHandler(new ClientRestHandler());
        registerRouteHandler(new ArchiveCommandRestHandler());
        registerRouteHandler(new ArchiveDownloadRestHandler());
        registerRouteHandler(new ArchiveIndexDownloadsRestHandler());
        registerRouteHandler(new ProcessorRestHandler());
        registerRouteHandler(new ProcessorCommandQueueRestHandler());
    }

    private void addApi(Api<Context> api) {
        apis.add(api);

        List<RouteConfig> routeConfigs = new ArrayList<>();
        for (MethodDescriptor method : api.getDescriptorForType().getMethods()) {
            RpcDescriptor descriptor = protobufRegistry.getRpc(method.getFullName());
            if (descriptor == null) {
                throw new UnsupportedOperationException("Unable to find rpc definition: " + method.getFullName());
            }
            routeConfigs.add(new RouteConfig(api, descriptor.getHttpRoute(), descriptor));
            for (HttpRoute route : descriptor.getAdditionalHttpRoutes()) {
                routeConfigs.add(new RouteConfig(api, route, descriptor));
            }
        }

        registerRouteConfigs(null, routeConfigs);
    }

    public void registerRouteHandler(RouteHandler routeHandler) {
        registerRouteHandler(null, routeHandler);
    }

    public void registerRouteHandler(String yamcsInstance, RouteHandler routeHandler) {
        // Using method handles for better invoke performance
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Method[] methods = getAnnotatedMethods(routeHandler.getClass());

        List<RouteConfig> routeConfigs = new ArrayList<>();
        try {
            for (int i = 0; i < methods.length; i++) {
                Method reflectedMethod = methods[i];
                MethodHandle handle = lookup.unreflect(reflectedMethod);

                Route[] anns = reflectedMethod.getAnnotationsByType(Route.class);
                for (Route ann : anns) {
                    for (String m : ann.method()) {
                        HttpMethod httpMethod = HttpMethod.valueOf(m);

                        routeConfigs.add(new RouteConfig(routeHandler, ann.path(),
                                ann.dataLoad(), ann.offThread(), ann.maxBodySize(), httpMethod, handle));
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(
                    "Could not access @Route annotated method in " + routeHandler.getClass());
        }

        registerRouteConfigs(yamcsInstance, routeConfigs);
    }

    private void registerRouteConfigs(String yamcsInstance, List<RouteConfig> routeConfigs) {
        // Sort in a way that increases chances of a good URI match
        Collections.sort(routeConfigs);

        List<RouteElement> targetRoutes = (yamcsInstance == null) ? defaultRoutes : dynamicRoutes;

        for (RouteConfig routeConfig : routeConfigs) {
            String routeString = routeConfig.uriTemplate;
            if (yamcsInstance != null) { // Expand {instance} upon registration (only for dynamic routes)
                if (!routeString.contains("{instance}")) {
                    log.warn("Dynamically added route {} {} is instance-specific, yet does not "
                            + "contain '{instance}' in its url. Routing of incoming requests "
                            + "will be ambiguous.", routeConfig.httpMethod, routeConfig.uriTemplate);
                }
                routeString = routeString.replace(":instance", yamcsInstance); // Legacy, remove some day
                routeString = routeString.replace("{instance}", yamcsInstance);
            }
            Pattern pattern = toPattern(routeString);
            Map<HttpMethod, RouteConfig> configByMethod = createAndGet(targetRoutes, pattern).configByMethod;
            configByMethod.put(routeConfig.httpMethod, routeConfig);
        }
    }

    private Method[] getAnnotatedMethods(Class<?> clazz) {
        List<Method> result = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Route.class) || method.isAnnotationPresent(Routes.class)) {
                result.add(method);
            }
        }
        clazz = clazz.getSuperclass();
        if (clazz != null) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Route.class) || method.isAnnotationPresent(Routes.class)) {
                    result.add(method);
                }
            }
        }
        return result.toArray(new Method[result.size()]);
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
                log.debug("No route matching URI: '{}'", req.uri());
                HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.NOT_FOUND);
                return false;
            }
            if (match.routeConfig.isDeprecated()) {
                log.warn("A client used a deprecated endpoint: {}", match.routeConfig.uriTemplate);
            }
            ctx.channel().attr(CTX_ROUTE_MATCH).set(match);
            RouteConfig rc = match.getRouteConfig();
            rc.requestCount.incrementAndGet();
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
        RouteMatch match = ctx.channel().attr(CTX_ROUTE_MATCH).get();
        User user = ctx.channel().attr(HttpRequestHandler.CTX_USER).get();
        String uri = HttpUtils.getUriWithoutContext(req, contextPath);
        QueryStringDecoder qsDecoder = new QueryStringDecoder(uri);
        RestRequest restReq = new RestRequest(ctx, req, qsDecoder, user);
        restReq.setRouteMatch(match);
        log.debug("R{}: Handling REST Request {} {}", restReq.getRequestId(), req.method(), req.uri());

        // Track status for metric purposes
        restReq.getCompletableFuture().whenComplete((channelFuture, e) -> {
            if (restReq.statusCode == 0) {
                log.warn("R{}: Status code not reported", restReq.getRequestId());
            } else if (restReq.statusCode < 200 || restReq.statusCode >= 300) {
                match.routeConfig.errorCount.incrementAndGet();
            }
        });

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
            if (match.routeConfig.api == null) {
                RouteHandler target = match.routeConfig.routeHandler;
                match.routeConfig.handle.invoke(target, req);
            } else {
                Api<Context> api = match.routeConfig.api;
                dispatchApiMethod(req, api, match.routeConfig);
            }
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
            handleException(req, t);
            req.getCompletableFuture().completeExceptionally(t);
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

    private void dispatchApiMethod(RestRequest restRequest, Api<Context> api, RouteConfig routeConfig) {
        Context apiContext = new Context(restRequest);

        String methodName = routeConfig.getDescriptor().getMethod();
        MethodDescriptor method = api.getDescriptorForType().findMethodByName(methodName);
        Message requestMessage;
        try {
            requestMessage = HttpTranscoder.transcode(restRequest, api, method, routeConfig);
        } catch (HttpTranscodeException e) {
            throw new BadRequestException(e.getMessage());
        }

        Observer<Message> observer = null;
        if (method.toProto().getServerStreaming()) {
            observer = new ServerStreamingObserver(apiContext);
        } else {
            observer = new CallObserver(restRequest);
        }

        api.callMethod(method, apiContext, requestMessage, observer);
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

    private Pattern toPattern(String route) {
        Matcher matcher = ROUTE_PATTERN.matcher(route);
        StringBuffer buf = new StringBuffer("^");
        while (matcher.find()) {
            boolean star = ("*".equals(matcher.group(3)));
            boolean optional = ("?".equals(matcher.group(3)));
            if ("**".equals(matcher.group(3))) {
                star = true;
                optional = true;
            }
            String slash = (matcher.group(1) != null) ? matcher.group(1) : "";
            StringBuilder replacement = new StringBuilder();
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

    public List<RouteInfo> getRouteInfoSet() {
        List<RouteInfo> routes = new ArrayList<>();
        for (RouteElement re : defaultRoutes) {
            re.configByMethod.values().forEach(v -> {
                RouteInfo.Builder routeb = RouteInfo.newBuilder();
                routeb.setHttpMethod(v.httpMethod.toString());
                routeb.setUrl(contextPath + v.uriTemplate);
                routeb.setRequestCount(v.requestCount.get());
                routeb.setErrorCount(v.errorCount.get());
                RpcDescriptor descriptor = v.getDescriptor();
                if (descriptor != null) {
                    routeb.setService(descriptor.getService());
                    routeb.setMethod(descriptor.getMethod());
                    routeb.setInputType(descriptor.getInputType().getName());
                    routeb.setOutputType(descriptor.getOutputType().getName());
                    if (descriptor.getDescription() != null) {
                        routeb.setDescription(descriptor.getDescription());
                    }
                    if (v.isDeprecated()) {
                        routeb.setDeprecated(true);
                    }
                }
                routes.add(routeb.build());
            });
        }
        return routes;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Closing channel due to exception", cause);
        ctx.close();
    }
}
