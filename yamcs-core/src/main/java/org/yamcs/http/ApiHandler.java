package org.yamcs.http;

import static org.yamcs.http.HttpRequestHandler.CTX_CONTEXT;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

import org.yamcs.YConfiguration;
import org.yamcs.security.User;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;

public class ApiHandler extends HttpHandler {

    private HttpServer httpServer;
    private RouteHandler routeHandler;
    private YConfiguration wsConfig;

    public ApiHandler(HttpServer httpServer) {
        this.httpServer = httpServer;

        var maxPageSize = httpServer.getConfig().getInt("maxPageSize");
        routeHandler = new RouteHandler(maxPageSize);

        wsConfig = httpServer.getConfig().getConfig("webSocket");
    }

    @Override
    public boolean requireAuth() {
        return true;
    }

    @Override
    public void handle(HandlerContext ctx) {
        /*
         * At this point we do not have the full request (only the header) so we have to configure the pipeline either
         * for receiving the full request or with route specific pipeline for receiving (large amounts of) data in case
         * of dataLoad routes.
         */

        var nettyContext = ctx.getNettyChannelHandlerContext();
        var nettyRequest = ctx.getNettyHttpRequest();
        var uri = HttpUtils.getPathWithoutContext(nettyRequest, ctx.getContextPath());
        if (uri.equals(HttpServer.WEBSOCKET_ROUTE.getGet())) {
            if (nettyRequest.method() == HttpMethod.GET) {
                prepareChannelForWebSocketUpgrade(nettyContext, nettyRequest, ctx.getUser());
                return;
            } else {
                throw new MethodNotAllowedException(nettyRequest.method(), uri, Arrays.asList(HttpMethod.GET));
            }
        }

        var match = matchRoute(nettyRequest.method(), uri);
        if (match == null) {
            throw new NotFoundException();
        }

        var routeContext = new RouteContext(httpServer, nettyContext, ctx.getUser(), nettyRequest, match.route,
                match.regexMatch);
        log.debug("{}: Routing {} {}", routeContext, nettyRequest.method(), nettyRequest.uri());

        nettyContext.channel().attr(CTX_CONTEXT).set(routeContext);

        var pipeline = nettyContext.pipeline();

        if (routeContext.isClientStreaming()) {
            pipeline.addLast(new HttpContentToByteBufDecoder());
            pipeline.addLast(new ProtobufVarint32FrameDecoder());

            String body = routeContext.getBodySpecifier();
            Message bodyPrototype = routeContext.getRequestPrototype();
            if (body != null && !"*".equals(body)) {
                FieldDescriptor field = bodyPrototype.getDescriptorForType().findFieldByName(body);
                bodyPrototype = bodyPrototype.newBuilderForType().getFieldBuilder(field)
                        .getDefaultInstanceForType();
            }
            pipeline.addLast(new ProtobufDecoder(bodyPrototype));
            pipeline.addLast(new StreamingClientHandler(routeContext));

            if (HttpUtil.is100ContinueExpected(nettyRequest)) {
                nettyContext.writeAndFlush(HttpUtils.CONTINUE_RESPONSE.retainedDuplicate());
            }
        } else {
            pipeline.addLast(new HttpContentCompressor());

            // this will cause the routeHandler read to be called as soon as the request is complete
            // it will also reject requests whose body is greater than the MAX_BODY_SIZE)
            pipeline.addLast(new HttpObjectAggregator(routeContext.getMaxBodySize()));
            pipeline.addLast(routeHandler);
            nettyContext.fireChannelRead(nettyRequest);
        }
    }

    /**
     * Adapts Netty's pipeline for allowing WebSocket upgrade
     *
     * @param ctx
     *            context for this channel handler
     */
    private void prepareChannelForWebSocketUpgrade(ChannelHandlerContext nettyContext, HttpRequest req, User user) {
        int maxFrameLength = wsConfig.getInt("maxFrameLength");
        int lo = wsConfig.getConfig("writeBufferWaterMark").getInt("low");
        int hi = wsConfig.getConfig("writeBufferWaterMark").getInt("high");
        var waterMark = new WriteBufferWaterMark(lo, hi);

        var pipeline = nettyContext.pipeline();
        pipeline.addLast(new HttpObjectAggregator(65536));
        pipeline.addLast(new WebSocketFrameDropper(waterMark.high()));
        pipeline.addLast(new WebSocketServerCompressionHandler());

        // Add websocket-specific handlers to channel pipeline
        String webSocketPath = req.uri();
        String subprotocols = "json, protobuf";
        pipeline.addLast(new WebSocketServerProtocolHandler(webSocketPath, subprotocols, true, maxFrameLength));

        // Emit idle events (interpreted by WebSocketFrameHandler).
        // Useful for avoiding unexpected closes when there's no activity.
        var pingWhenIdleFor = wsConfig.getInt("pingWhenIdleFor");
        if (pingWhenIdleFor > 0) {
            pipeline.addLast(new IdleStateHandler(0, 0, pingWhenIdleFor));
        }

        pipeline.addLast(new WebSocketFrameHandler(httpServer, req, user, waterMark));

        // Effectively trigger websocket-handler (will attempt handshake)
        nettyContext.fireChannelRead(req);
    }

    private RouteMatch matchRoute(HttpMethod method, String uri) throws MethodNotAllowedException {
        for (Route route : httpServer.getRoutes()) {
            if (route.getHttpMethod().equals(method)) {
                Matcher matcher = route.matchURI(uri);
                if (matcher.matches()) {
                    if (route.isDeprecated()) {
                        log.warn("A client used a deprecated route: {}", uri);
                    }

                    return new RouteMatch(matcher, route);
                }
            }
        }

        // Second pass, in case we did not find an exact match
        Set<HttpMethod> allowedMethods = new HashSet<>(4);
        for (Route route : httpServer.getRoutes()) {
            Matcher matcher = route.matchURI(uri);
            if (matcher.matches()) {
                allowedMethods.add(method);
            }
        }
        if (!allowedMethods.isEmpty()) {
            throw new MethodNotAllowedException(method, uri, allowedMethods);
        }

        return null;
    }

    /**
     * Represents a matched route pattern
     */
    private static final class RouteMatch {
        final Matcher regexMatch;
        final Route route;

        RouteMatch(Matcher regexMatch, Route route) {
            this.regexMatch = regexMatch;
            this.route = route;
        }
    }
}
