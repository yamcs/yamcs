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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsVersion;
import org.yamcs.parameterarchive.ParameterArchiveMaintenanceRestHandler;
import org.yamcs.protobuf.Rest.GetApiOverviewResponse;
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
import org.yamcs.web.rest.archive.ArchiveParameter2RestHandler;
import org.yamcs.web.rest.archive.ArchiveParameterRestHandler;
import org.yamcs.web.rest.archive.ArchiveStreamRestHandler;
import org.yamcs.web.rest.archive.ArchiveTableRestHandler;
import org.yamcs.web.rest.archive.ArchiveTagRestHandler;
import org.yamcs.web.rest.mdb.MDBAlgorithmRestHandler;
import org.yamcs.web.rest.mdb.MDBCommandRestHandler;
import org.yamcs.web.rest.mdb.MDBContainerRestHandler;
import org.yamcs.web.rest.mdb.MDBParameterRestHandler;
import org.yamcs.web.rest.mdb.MDBRestHandler;
import org.yamcs.web.rest.processor.ProcessorCommandQueueRestHandler;
import org.yamcs.web.rest.processor.ProcessorCommandRestHandler;
import org.yamcs.web.rest.processor.ProcessorParameterRestHandler;
import org.yamcs.web.rest.processor.ProcessorRestHandler;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
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
 * These latter routes usually mention ':instance' in their url, which will be
 * expanded upon registration into the actual yamcs instance.
 */
public class Router {

    private static final Pattern ROUTE_PATTERN = Pattern.compile("(\\/)?:(\\w+)([\\?\\*])?");
    private static final Logger log = LoggerFactory.getLogger(Router.class);

    // Order, because patterns are matched top-down in insertion order
    private LinkedHashMap<Pattern, Map<HttpMethod, RouteConfig>> defaultRoutes = new LinkedHashMap<>();
    private LinkedHashMap<Pattern, Map<HttpMethod, RouteConfig>> dynamicRoutes = new LinkedHashMap<>();

    public Router() {
        registerRouteHandler(null, new ClientRestHandler());
        registerRouteHandler(null, new DisplayRestHandler());
        registerRouteHandler(null, new InstanceRestHandler());
        registerRouteHandler(null, new LinkRestHandler());
        registerRouteHandler(null, new UserRestHandler());

        registerRouteHandler(null, new ArchiveAlarmRestHandler());
        registerRouteHandler(null, new ArchiveCommandRestHandler());
        registerRouteHandler(null, new ArchiveDownloadRestHandler());
        registerRouteHandler(null, new ArchiveEventRestHandler());
        registerRouteHandler(null, new ArchiveIndexRestHandler());
        registerRouteHandler(null, new ArchivePacketRestHandler());
        registerRouteHandler(null, new ArchiveParameterRestHandler());
        registerRouteHandler(null, new ParameterArchiveMaintenanceRestHandler());
        registerRouteHandler(null, new ArchiveParameter2RestHandler());
        registerRouteHandler(null, new ArchiveStreamRestHandler());
        registerRouteHandler(null, new ArchiveTableRestHandler());
        registerRouteHandler(null, new ArchiveTagRestHandler());

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
    public void registerRouteHandler(String instance, RouteHandler routeHandler) {
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
        targetRoutes = (instance == null) ? defaultRoutes : dynamicRoutes;

        for (RouteConfig routeConfig : routeConfigs) {
            String routeString = routeConfig.originalPath;
            if (instance != null) { // Expand :instance upon registration (only for dynamic routes)
                routeString = routeString.replace(":instance", instance);
            }
            Pattern pattern = toPattern(routeString);
            targetRoutes.putIfAbsent(pattern, new LinkedHashMap<>());
            Map<HttpMethod, RouteConfig> configByMethod = targetRoutes.get(pattern);
            configByMethod.put(routeConfig.httpMethod, routeConfig);
        }
    }

    public void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req, AuthenticationToken token) {

        QueryStringDecoder qsDecoder = new QueryStringDecoder(req.getUri());
        RestRequest restReq = new RestRequest(ctx, req, qsDecoder, token);

        try {
            // Decode first the path/qs difference, then url-decode the path
            String uri = new URI(qsDecoder.path()).getPath();

            RouteMatch match = matchURI(req.getMethod(), uri);
            restReq.setRouteMatch(match);
            if (match != null) {
                dispatch(restReq, match);
            } else {
                log.info("No route matching URI: '{}'", req.getUri());
                HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.NOT_FOUND);
            }
        } catch (URISyntaxException e) {
            RestHandler.sendRestError(restReq, HttpResponseStatus.INTERNAL_SERVER_ERROR, e);
        } catch (MethodNotAllowedException e) {
            log.info("Method {} not allowed for URI: '{}'", req.getMethod(), req.getUri());
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
        try {
            RouteHandler target = match.routeConfig.routeHandler;

            // FIXME handleRequest must never return null! Futures are used to follow up on handling
            Object o = match.routeConfig.handle.invoke(target, req);
            if (o == null) {
                log.error("handler {} does not return a CompletableFuture", match.routeConfig.handle);
                return; 
            }
            if(o instanceof ChannelFuture) {
                handleCompletion((ChannelFuture) o);
            } else if(o instanceof CompletableFuture<?>) {
                CompletableFuture<ChannelFuture> cf =  (CompletableFuture<ChannelFuture>)o;
                cf.whenComplete((channelFuture, e) -> {
                    if(e!=null) {
                        handleException(req, e);
                    } else {
                        handleCompletion((ChannelFuture) o);
                    }
                });
            }
        } catch(Throwable t) {
            handleException(req, t);
        }
    }

    private void handleCompletion(ChannelFuture responseFuture) {
        /**
         * Follow-up on the successful write, to provide some hints when a future was not actually
         * successfully delivered.
         */
        responseFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    log.error("Error writing out response to client", future.cause());
                    future.channel().close();
                }
            }
        });
    }
    private void handleException(RestRequest req, Throwable t) {
        if(t instanceof InternalServerErrorException) {
            InternalServerErrorException e = (InternalServerErrorException)t;
            log.error("Reporting internal server error to client", e);
            RestHandler.sendRestError(req, e.getStatus(), e);
        } else if (t instanceof HttpException) {
            HttpException e = (HttpException)t;
            log.warn("Sending nominal exception back to client: {}", e.getMessage());
            RestHandler.sendRestError(req, e.getStatus(), e);
        } else {
            log.error("Unexpected error " + t, t);
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
        return Pattern.compile(buf.append("$").toString());
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
     * 'Documents' all registered resources
     */
    private final class OverviewRouteHandler extends RestHandler {

        @Route(path="/api", method="GET")
        public ChannelFuture getApiOverview(RestRequest req) throws HttpException {
            GetApiOverviewResponse.Builder responseb = GetApiOverviewResponse.newBuilder();
            responseb.setYamcsVersion(YamcsVersion.version);

            // Unique accross http methods, and according to insertion order
            Set<String> urls = new LinkedHashSet<>();
            for (Map<HttpMethod, RouteConfig> map : defaultRoutes.values()) {
                map.values().forEach(v -> urls.add(v.originalPath));
            }

            urls.forEach(url -> responseb.addUrl(url));

            return sendOK(req, responseb.build(), SchemaRest.GetApiOverviewResponse.WRITE);
        }
    }
}
