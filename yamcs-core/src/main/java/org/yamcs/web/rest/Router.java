package org.yamcs.web.rest;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsVersion;
import org.yamcs.parameterarchive.ParameterArchiveMaintenanceRestHandler;
import org.yamcs.protobuf.Rest.GetApiOverviewResponse;
import org.yamcs.protobuf.Rest.GetApiOverviewResponse.RouteInfo;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.web.HttpException;
import org.yamcs.web.HttpRequestHandler;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.MethodNotAllowedException;
import org.yamcs.web.RouteHandler;
import org.yamcs.web.rest.archive.ArchiveAlarmRestHandler;
import org.yamcs.web.rest.archive.ArchiveCommandRestHandler;
import org.yamcs.web.rest.archive.ArchiveDownloadRestHandler;
import org.yamcs.web.rest.archive.ArchiveEventRestHandler;
import org.yamcs.web.rest.archive.ArchiveIndexRestHandler;
import org.yamcs.web.rest.archive.ArchivePacketRestHandler;
import org.yamcs.web.rest.archive.ArchiveParameterRestHandler;
import org.yamcs.web.rest.archive.ArchiveParameterReplayRestHandler;
import org.yamcs.web.rest.archive.ArchiveStreamRestHandler;
import org.yamcs.web.rest.archive.ArchiveTableRestHandler;
import org.yamcs.web.rest.archive.ArchiveTagRestHandler;
import org.yamcs.web.rest.archive.RocksDbMaintenanceRestHandler;
import org.yamcs.web.rest.mdb.MDBAlgorithmRestHandler;
import org.yamcs.web.rest.mdb.MDBCommandRestHandler;
import org.yamcs.web.rest.mdb.MDBContainerRestHandler;
import org.yamcs.web.rest.mdb.MDBParameterRestHandler;
import org.yamcs.web.rest.mdb.MDBRestHandler;
import org.yamcs.web.rest.processor.ProcessorCommandQueueRestHandler;
import org.yamcs.web.rest.processor.ProcessorCommandRestHandler;
import org.yamcs.web.rest.processor.ProcessorParameterRestHandler;
import org.yamcs.web.rest.processor.ProcessorRestHandler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;

/**
 * Matches a request uri to a registered route handler. Stops on the first
 * match.
 * <p>
 * The Router itself has the same granularity as HttpServer: one instance only.
 * <p>
 * When matching a route, priority is first given to built-in routes, only if
 * none match the first matching instance-specific dynamic route is matched.
 * Dynamic routes often mention ':instance' in their url, which will be
 * expanded upon registration into the actual yamcs instance.
 */
public class Router {

    private static final Pattern ROUTE_PATTERN = Pattern.compile("(\\/)?:(\\w+)([\\?\\*])?");
    private static final Logger log = LoggerFactory.getLogger(Router.class);

    // Order, because patterns are matched top-down in insertion order
    private LinkedHashMap<Pattern, Map<HttpMethod, RouteConfig>> defaultRoutes = new LinkedHashMap<>();
    private LinkedHashMap<Pattern, Map<HttpMethod, RouteConfig>> dynamicRoutes = new LinkedHashMap<>();

    private boolean logSlowRequests = true;
    int SLOW_REQUEST_TIME = 20;//seconds; requests that execute more than this are logged
    ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);



    public Router() {
        registerRouteHandler(null, new ClientRestHandler());
        registerRouteHandler(null, new DisplayRestHandler());
        registerRouteHandler(null, new InstanceRestHandler());
        registerRouteHandler(null, new LinkRestHandler());
        registerRouteHandler(null, new UserRestHandler());
        registerRouteHandler(null, new ServiceRestHandler());

        registerRouteHandler(null, new ArchiveAlarmRestHandler());
        registerRouteHandler(null, new ArchiveCommandRestHandler());
        registerRouteHandler(null, new ArchiveDownloadRestHandler());
        registerRouteHandler(null, new ArchiveEventRestHandler());
        registerRouteHandler(null, new ArchiveIndexRestHandler());
        registerRouteHandler(null, new ArchivePacketRestHandler());        
        registerRouteHandler(null, new ParameterArchiveMaintenanceRestHandler());
        registerRouteHandler(null, new ArchiveParameterRestHandler());
        registerRouteHandler(null, new ArchiveStreamRestHandler());
        registerRouteHandler(null, new ArchiveTableRestHandler());
        registerRouteHandler(null, new ArchiveTagRestHandler());
        registerRouteHandler(null, new RocksDbMaintenanceRestHandler());

        registerRouteHandler(null, new ProcessorRestHandler());
        registerRouteHandler(null, new ProcessorParameterRestHandler());
        registerRouteHandler(null, new ProcessorCommandRestHandler());
        registerRouteHandler(null, new ProcessorCommandQueueRestHandler());

        registerRouteHandler(null, new MDBRestHandler());
        registerRouteHandler(null, new MDBParameterRestHandler());
        registerRouteHandler(null, new MDBContainerRestHandler());
        registerRouteHandler(null, new MDBCommandRestHandler());
        registerRouteHandler(null, new MDBAlgorithmRestHandler());

        registerRouteHandler(null, new OverviewRouteHandler());
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
                if (reflectedMethod.isAnnotationPresent(Route.class) || reflectedMethod.isAnnotationPresent(Routes.class)) {
                    MethodHandle handle = lookup.unreflect(reflectedMethod);

                    Route[] anns = reflectedMethod.getDeclaredAnnotationsByType(Route.class);
                    for (Route ann : anns) {
                        for (String m : ann.method()) {
                            HttpMethod httpMethod = HttpMethod.valueOf(m);
                            routeConfigs.add(new RouteConfig(routeHandler, ann.path(), ann.priority(), httpMethod, handle));
                        }
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Could not access @Route annotated method in " + routeHandler.getClass());
        }

        // Sort in a way that increases chances of a good URI match
        // 1. @Route(priority=true) first
        // 2. Descending on path length
        // 3. Actual path contents (should not matter too much)
        Collections.sort(routeConfigs);

        LinkedHashMap<Pattern, Map<HttpMethod, RouteConfig>> targetRoutes;
        targetRoutes = (yamcsInstance == null) ? defaultRoutes : dynamicRoutes;

        for (RouteConfig routeConfig : routeConfigs) {
            String routeString = routeConfig.originalPath;
            if (yamcsInstance != null) { // Expand :instance upon registration (only for dynamic routes)
                if (!routeString.contains(":instance")) {
                    log.warn("Dynamically added route {} {} is instance-specific, yet does not "
                            + ", contain ':instance' in its url. Routing of incoming requests "
                            + "will be ambiguous.", routeConfig.httpMethod, routeConfig.originalPath);
                }
                routeString = routeString.replace(":instance", yamcsInstance);
            }
            Pattern pattern = toPattern(routeString);
            targetRoutes.putIfAbsent(pattern, new LinkedHashMap<>());
            Map<HttpMethod, RouteConfig> configByMethod = targetRoutes.get(pattern);
            configByMethod.put(routeConfig.httpMethod, routeConfig);
        }
    }

    public void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req, AuthenticationToken token, QueryStringDecoder qsDecoder) {
        RestRequest restReq = new RestRequest(ctx, req, qsDecoder, token);

        try {
            // Decode first the path/qs difference, then url-decode the path
            String uri = new URI(qsDecoder.path()).getPath();
            log.debug("R{}: Handling REST Request {} {}", restReq.getRequestId(), req.getMethod(), uri);
            
            RouteMatch match = matchURI(req.getMethod(), uri);
            restReq.setRouteMatch(match);
            if (match != null) {
                dispatch(restReq, match);
            } else {
                log.info("R{}: No route matching URI: '{}'", restReq.getRequestId(), req.getUri());
                HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.NOT_FOUND);
            }
        } catch (URISyntaxException e) {
            RestHandler.sendRestError(restReq, HttpResponseStatus.INTERNAL_SERVER_ERROR, e);
        } catch (MethodNotAllowedException e) {
            log.info("R{}: Method {} not allowed for URI: '{}'", restReq.getRequestId(), req.getMethod(), req.getUri());
            RestHandler.sendRestError(restReq, e.getStatus(), e);
        }
    }

    protected RouteMatch matchURI(HttpMethod method, String uri) throws MethodNotAllowedException {
        Set<HttpMethod> allowedMethods = null;
        for (Entry<Pattern, Map<HttpMethod, RouteConfig>> entry : defaultRoutes.entrySet()) {
            Matcher matcher = entry.getKey().matcher(uri);
            if (matcher.matches()) {
                Map<HttpMethod, RouteConfig> byMethod = entry.getValue();
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

        for (Entry<Pattern, Map<HttpMethod, RouteConfig>> entry : dynamicRoutes.entrySet()) {
            Matcher matcher = entry.getKey().matcher(uri);
            if (matcher.matches()) {
                Map<HttpMethod, RouteConfig> byMethod = entry.getValue();
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

        ScheduledFuture<?> x = timer.schedule(() ->{
            log.error("R{} blocking the netty thread for 2 seconds. uri: {}", req.getRequestId(), req.getHttpRequest().getUri());
        }
        , 2, TimeUnit.SECONDS);

        //the handlers will send themselves the response unless they throw an exception, case which is handled in the catch below.
        try {
            RouteHandler target = match.routeConfig.routeHandler;
            match.routeConfig.handle.invoke(target, req);
            req.getCompletableFuture().whenComplete((channelFuture, e) -> {
                if(e!=null) {
                    log.debug("R{}: REST request execution finished with error: {}, transferred bytes: {}", req.getRequestId(), e.getMessage(), req.getTransferredSize());
                } else {
                    log.debug("R{}: REST request execution finished successfully, transferred bytes: {}", req.getRequestId(), req.getTransferredSize());
                }
            });
        } catch(Throwable t) {
            req.getCompletableFuture().completeExceptionally(t);
            handleException(req, t);
        }
        x.cancel(true);

        CompletableFuture<Void> cf = req.getCompletableFuture();
        if(logSlowRequests) {
            timer.schedule(() ->{
                if(!cf.isDone()) {
                    log.warn("R{} executing for more than 20 seconds. uri: {}", req.getRequestId(), req.getHttpRequest().getUri());
                }
            }
            , 20, TimeUnit.SECONDS);
        }
    }

    private void handleException(RestRequest req, Throwable t) {
        if (t instanceof InternalServerErrorException) {
            InternalServerErrorException e = (InternalServerErrorException) t;
            log.error(String.format("R%d: Reporting internal server error to client", req.getRequestId()), e);
            RestHandler.sendRestError(req, e.getStatus(), e);
        } else if (t instanceof HttpException) {
            HttpException e = (HttpException)t;
            log.warn("R{}: Sending nominal exception back to client: {}", req.getRequestId(), e.getMessage());
            RestHandler.sendRestError(req, e.getStatus(), e);
        } else {
            log.error(String.format("R%d: Reporting internal server error to client", req.getRequestId()), t);
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

        RouteConfig(RouteHandler routeHandler, String originalPath, boolean priority, HttpMethod httpMethod, MethodHandle handle) {
            this.routeHandler = routeHandler;
            this.originalPath = originalPath;
            this.priority = priority;
            this.httpMethod = httpMethod;
            this.handle = handle;
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
    }

    /**
     * 'Documents' all registered resources, and provides some
     * general server information.
     */
    private final class OverviewRouteHandler extends RestHandler {

        @Route(path="/api", method="GET")
        public void getApiOverview(RestRequest req) throws HttpException {
            GetApiOverviewResponse.Builder responseb = GetApiOverviewResponse.newBuilder();
            responseb.setYamcsVersion(YamcsVersion.version);
            responseb.setServerId(YamcsServer.getServerId());

            // Property to be interpreted at client's leisure.
            // Concept of defaultInstance could be moved into YamcsServer
            // at some point, but there's for now unsufficient support.
            // (would need websocket adjmustments, which are now
            // instance-specific).
            YConfiguration yconf = YConfiguration.getConfiguration("yamcs");
            if (yconf.containsKey("defaultInstance")) {
                responseb.setDefaultYamcsInstance(yconf.getString("defaultInstance"));
            } else {
                Set<String> instances = YamcsServer.getYamcsInstanceNames();
                if (!instances.isEmpty()) {
                    responseb.setDefaultYamcsInstance(instances.iterator().next());
                }
            }

            // Aggregate to unique urls, and keep insertion order
            Map<String, RouteInfo.Builder> builders = new LinkedHashMap<>();
            for (Map<HttpMethod, RouteConfig> map : defaultRoutes.values()) {
                map.values().forEach(v -> {
                    RouteInfo.Builder builder = builders.get(v.originalPath);
                    if (builder == null) {
                        builder = RouteInfo.newBuilder();
                        builders.put(v.originalPath, builder);
                    }
                    builder.setUrl(v.originalPath).addMethod(v.httpMethod.toString());
                });
            }
            builders.values().forEach(b -> responseb.addRoute(b));
            completeOK(req, responseb.build(), SchemaRest.GetApiOverviewResponse.WRITE);
        }
    }
}
