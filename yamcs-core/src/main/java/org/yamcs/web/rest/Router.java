package org.yamcs.web.rest;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.web.HttpException;
import org.yamcs.web.HttpHandler;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.MethodNotAllowedException;
import org.yamcs.web.RouteHandler;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;

/**
 * Matches a request uri to a registered route handler. Stops on the first match.
 */
public class Router {
    
    private static final Pattern ROUTE_PATTERN = Pattern.compile("(\\/)?:(\\w+)([\\?\\*])?");
    private static final Logger log = LoggerFactory.getLogger(Router.class);

    // Order, because patterns are matched top-down in insertion order
    private Map<Pattern, Map<HttpMethod, RouteConfig>> routes = new LinkedHashMap<>();
    
    // Using method handles for better invoke performance
    public void registerRouteHandler(RouteHandler routeHandler) {
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
        
        for (RouteConfig routeConfig : routeConfigs) {
            Pattern pattern = toPattern(routeConfig.originalPath);
            routes.putIfAbsent(pattern, new LinkedHashMap<>());
            Map<HttpMethod, RouteConfig> configByMethod = routes.get(pattern);
            configByMethod.put(routeConfig.httpMethod, routeConfig);
        }
    }
    
    public void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req, AuthenticationToken token) {
        QueryStringDecoder qsDecoder = new QueryStringDecoder(req.getUri());
        RestRequest restReq = new RestRequest(ctx, req, qsDecoder, token);
        try {
            RouteMatch match = matchURI(req.getMethod(), qsDecoder.path() /* without query string ! */);
            if (match != null) {
                dispatch(restReq, match);
            } else {
                HttpHandler.sendPlainTextError(ctx, HttpResponseStatus.NOT_FOUND);
            }
        } catch (MethodNotAllowedException e) {
            RestHandler.sendRestError(restReq, e.getStatus(), e);
        }
    }
    
    protected RouteMatch matchURI(HttpMethod method, String uri) throws MethodNotAllowedException {
        Set<HttpMethod> allowedMethods = null;
        for (Entry<Pattern, Map<HttpMethod, RouteConfig>> entry : routes.entrySet()) {
            Matcher matcher = entry.getKey().matcher(uri);
            if (matcher.matches()) {
                Map<HttpMethod, RouteConfig> byMethod = entry.getValue();
                if (byMethod.containsKey(method)) {
                    return new RouteMatch(matcher.toMatchResult(), byMethod.get(method));
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
    
    public void dispatch(RestRequest req, RouteMatch match) {
        try {
            RestHandler target = (RestHandler) match.routeConfig.routeHandler;
            
            // FIXME handleRequest must never return null! Futures are used to follow up on handling
            ChannelFuture responseFuture = (ChannelFuture) match.routeConfig.handle.invoke(target, req);
            if (responseFuture == null) return; // Allowed, when the specific handler prefers to do this
            
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

        } catch (InternalServerErrorException e) {
            log.error("Reporting internal server error to client", e);
            RestHandler.sendRestError(req, e.getStatus(), e);
        } catch (HttpException e) {
            log.warn("Sending nominal exception back to client", e);
            RestHandler.sendRestError(req, e.getStatus(), e);
        } catch (Throwable t) {
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
                replacement.append(star ? "(.+?)" : "([^/]+)");
                replacement.append("?)?");
            } else {
                replacement.append(slash);
                replacement.append("(?:");
                replacement.append(star ? "(.+?)" : "([^/]+)");
                replacement.append(")");
            }            

            matcher.appendReplacement(buf, replacement.toString());
        }
        matcher.appendTail(buf);
        return Pattern.compile(buf.append("$").toString());
    }
    
    public Collection<Map<HttpMethod, RouteConfig>> getRoutes() {
        return routes.values();
    }
    
    /**
     * Struct containing all non-path route configuration
     */
    public final static class RouteConfig implements Comparable<RouteConfig> {
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
     * Represents a matched route pattern.
     * Used as a 'double' return value
     */
    public final static class RouteMatch {
        final MatchResult regexMatch;
        final RouteConfig routeConfig;
        
        RouteMatch(MatchResult regexMatch, RouteConfig routeConfig) {
            this.regexMatch = regexMatch;
            this.routeConfig = routeConfig;
        }
    }
}
