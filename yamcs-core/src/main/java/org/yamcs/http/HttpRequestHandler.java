package org.yamcs.http;

import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;

import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.http.auth.TokenStore;
import org.yamcs.http.websocket.WebSocketFrameHandler;
import org.yamcs.logging.Log;
import org.yamcs.security.AuthenticationException;
import org.yamcs.security.AuthenticationInfo;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.SecurityStore;
import org.yamcs.security.User;
import org.yamcs.security.UsernamePasswordToken;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

/**
 * Handles handshakes and messages.
 * 
 * A new instance of this handler is created for every request.
 *
 * We have following different request types
 * <ul>
 * <li>static requests - sent to the fileRequestHandler - do no go higher in the netty pipeline</li>
 * <li>websocket requests - the pipeline is modified to add the websocket handshaker.</li>
 * <li>load data requests - the pipeline is modified by the respective route handler</li>
 * <li>standard API calls (the vast majority) - the HttpObjectAgreggator is added upstream to collect (and limit) all
 * data from the http request in one object.</li>
 * </ul>
 * Because we support multiple http requests on one connection (keep-alive), we have to clean the pipeline when the
 * request type changes
 */
public class HttpRequestHandler extends ChannelInboundHandlerAdapter {

    public static final String ANY_PATH = "*";
    private static final String API_PATH = "api";
    private static final String STATIC_PATH = "static";
    private static final String WEBSOCKET_PATH = "_websocket";
    private static final String AUTH_TYPE_BASIC = "Basic ";
    private static final String AUTH_TYPE_BEARER = "Bearer ";

    public static final AttributeKey<HttpRequest> CTX_HTTP_REQUEST = AttributeKey.valueOf("httpRequest");
    public static final AttributeKey<RouteContext> CTX_CONTEXT = AttributeKey.valueOf("routeContext");

    private static final Log log = new Log(HttpRequestHandler.class);

    public static final Object CONTENT_FINISHED_EVENT = new Object();
    private static StaticFileHandler fileRequestHandler = new StaticFileHandler();
    private static SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();

    private static RouteHandler routeHandler = new RouteHandler();

    private HttpServer httpServer;
    private String contextPath;
    private boolean contentExpected = false;

    YConfiguration wsConfig;

    public HttpRequestHandler(HttpServer httpServer) {
        this.httpServer = httpServer;

        wsConfig = httpServer.getConfig().getConfig("webSocket");
        contextPath = httpServer.getContextPath();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        httpServer.trackClientChannel(ctx.channel());
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpMessage) {
            DecoderResult dr = ((HttpMessage) msg).decoderResult();
            if (!dr.isSuccess()) {
                log.warn("{} Exception while decoding http message: {}", ctx.channel().id().asShortText(), dr.cause());
                ctx.writeAndFlush(HttpUtils.EMPTY_BAD_REQUEST_RESPONSE);
                return;
            }
        }

        if (msg instanceof HttpRequest) {
            contentExpected = false;

            HttpRequest req = (HttpRequest) msg;

            // We have this also on info level coupled with the HTTP response status
            // code, but this is on debug for an earlier reporting while debugging issues
            log.debug("{} {} {}", ctx.channel().id().asShortText(), req.method(), req.uri());

            try {
                handleRequest(ctx, req);
            } catch (HttpException e) {
                log.warn("{}: {}", req.uri(), e.getMessage());
                sendPlainTextError(ctx, req, e.getStatus(), e.getMessage());
            } catch (Throwable t) {
                log.error("{}", req.uri(), t);
                sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }

            ReferenceCountUtil.release(msg);
        } else if (msg instanceof HttpContent) {
            if (contentExpected) {
                ctx.fireChannelRead(msg);
                if (msg instanceof LastHttpContent) {
                    ctx.fireUserEventTriggered(CONTENT_FINISHED_EVENT);
                }
            } else if (!(msg instanceof LastHttpContent)) {
                log.warn("{} unexpected http content received: {}", ctx.channel().id().asShortText(), msg);
                ReferenceCountUtil.release(msg);
                ctx.close();
            }
        } else {
            log.error("{} unexpected message received: {}", ctx.channel().id().asShortText(), msg);
            ReferenceCountUtil.release(msg);
        }
    }

    private void handleRequest(ChannelHandlerContext ctx, HttpRequest req) throws IOException {
        cleanPipeline(ctx.pipeline());
        ctx.channel().attr(CTX_HTTP_REQUEST).set(req);

        if (!req.uri().startsWith(contextPath)) {
            sendPlainTextError(ctx, req, NOT_FOUND);
            return;
        }

        String pathString = HttpUtils.getPathWithoutContext(req, contextPath);

        // Note: pathString starts with / so path[0] is always empty
        String[] path = pathString.split("/", 3);

        User user;

        switch (path[1]) {
        case STATIC_PATH:
            if (path.length == 2) { // do not accept "/static/" (i.e. directory listing) requests
                sendPlainTextError(ctx, req, FORBIDDEN);
                return;
            }
            fileRequestHandler.handleStaticFileRequest(ctx, req, path[2]);
            return;
        case API_PATH:
            user = authorizeUser(ctx, req);
            handleApiRequest(ctx, req, user, pathString);
            contentExpected = true;
            return;
        case WEBSOCKET_PATH:
            user = authorizeUser(ctx, req);
            if (path.length == 2) { // No instance specified
                prepareChannelForWebSocketUpgrade(ctx, req, null, null, user);
            } else {
                path = path[2].split("/", 2);
                if (YamcsServer.hasInstance(path[0])) {
                    if (path.length == 1) {
                        prepareChannelForWebSocketUpgrade(ctx, req, path[0], null, user);
                    } else {
                        prepareChannelForWebSocketUpgrade(ctx, req, path[0], path[1], user);
                    }
                } else {
                    sendPlainTextError(ctx, req, NOT_FOUND);
                }
            }
            return;
        }

        Handler handler = httpServer.createHandler(path[1]);
        if (handler == null) {
            handler = httpServer.createHandler(ANY_PATH);
        }
        if (handler != null) {
            ctx.pipeline().addLast(new HttpContentCompressor());
            ctx.pipeline().addLast(new HttpObjectAggregator(65536));
            ctx.pipeline().addLast(handler);
            ctx.fireChannelRead(req);
            contentExpected = true;
            return;
        }

        // Too bad.
        sendPlainTextError(ctx, req, NOT_FOUND);
    }

    private User authorizeUser(ChannelHandlerContext ctx, HttpRequest req) throws HttpException {
        if (req.headers().contains(HttpHeaderNames.AUTHORIZATION)) {
            String authorizationHeader = req.headers().get(HttpHeaderNames.AUTHORIZATION);
            if (authorizationHeader.startsWith(AUTH_TYPE_BASIC)) { // Exact case only
                return handleBasicAuth(ctx, req);
            } else if (authorizationHeader.startsWith(AUTH_TYPE_BEARER)) {
                return handleBearerAuth(ctx, req);
            } else {
                throw new BadRequestException("Unsupported Authorization header '" + authorizationHeader + "'");
            }
        }

        // There may be an access token in the cookie. This use case is added because
        // of web socket requests coming from the browser where it is not possible to
        // set custom authorization headers. It'd be interesting if we communicate the
        // access token via the websocket subprotocol instead (e.g. via temp. route).
        String accessToken = getAccessTokenFromCookie(req);
        if (accessToken != null) {
            return handleAccessToken(ctx, req, accessToken);
        }

        if (securityStore.getGuestUser().isActive()) {
            return securityStore.getGuestUser();
        } else {
            throw new UnauthorizedException("Missing 'Authorization' or 'Cookie' header");
        }
    }

    /**
     * At this point we do not have the full request (only the header) so we have to configure the pipeline either for
     * receiving the full request or with route specific pipeline for receiving (large amounts of) data in case of
     * dataLoad routes.
     */
    private void handleApiRequest(ChannelHandlerContext nettyContext, HttpRequest nettyRequest, User user, String uri) {
        if (uri.equals(HttpServer.WEBSOCKET_ROUTE.getGet())) {
            if (nettyRequest.method() == HttpMethod.GET) {
                prepareChannelForWebSocketUpgrade(nettyContext, nettyRequest, user);
                return;
            } else {
                throw new MethodNotAllowedException(nettyRequest.method(), uri, Arrays.asList(HttpMethod.GET));
            }
        }

        RouteMatch match = matchRoute(nettyRequest.method(), uri);
        if (match == null) {
            throw new NotFoundException();
        }

        RouteContext ctx = new RouteContext(httpServer, nettyContext, user, nettyRequest, match.route,
                match.regexMatch);
        log.debug("{}: Routing {} {}", ctx, nettyRequest.method(), nettyRequest.uri());

        nettyContext.channel().attr(CTX_CONTEXT).set(ctx);

        ChannelPipeline pipeline = nettyContext.pipeline();

        if (ctx.isClientStreaming()) {
            pipeline.addLast(new HttpContentToByteBufDecoder());
            pipeline.addLast(new ProtobufVarint32FrameDecoder());

            String body = ctx.getBodySpecifier();
            Message bodyPrototype = ctx.getRequestPrototype();
            if (body != null && !"*".equals(body)) {
                FieldDescriptor field = bodyPrototype.getDescriptorForType().findFieldByName(body);
                bodyPrototype = bodyPrototype.newBuilderForType().getFieldBuilder(field)
                        .getDefaultInstanceForType();
            }
            pipeline.addLast(new ProtobufDecoder(bodyPrototype));
            pipeline.addLast(new StreamingClientHandler(ctx));

            if (HttpUtil.is100ContinueExpected(nettyRequest)) {
                nettyContext.writeAndFlush(HttpUtils.CONTINUE_RESPONSE.retainedDuplicate());
            }
        } else {
            pipeline.addLast(new HttpContentCompressor());

            // this will cause the routeHandler read to be called as soon as the request is complete
            // it will also reject requests whose body is greater than the MAX_BODY_SIZE)
            pipeline.addLast(new HttpObjectAggregator(ctx.getMaxBodySize()));
            pipeline.addLast(routeHandler);
            nettyContext.fireChannelRead(nettyRequest);
        }
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
     * Adapts Netty's pipeline for allowing WebSocket upgrade
     *
     * @param ctx
     *            context for this channel handler
     */
    private void prepareChannelForWebSocketUpgrade(ChannelHandlerContext nettyContext, HttpRequest req, User user) {
        contentExpected = true;

        ChannelPipeline pipeline = nettyContext.pipeline();
        pipeline.addLast(new HttpObjectAggregator(65536));

        int maxFrameLength = wsConfig.getInt("maxFrameLength");
        int maxDropped = wsConfig.getInt("connectionCloseNumDroppedMsg");
        int lo = wsConfig.getConfig("writeBufferWaterMark").getInt("low");
        int hi = wsConfig.getConfig("writeBufferWaterMark").getInt("high");
        WriteBufferWaterMark waterMark = new WriteBufferWaterMark(lo, hi);

        // Add websocket-specific handlers to channel pipeline
        String webSocketPath = req.uri();
        String subprotocols = "json, protobuf";
        pipeline.addLast(new WebSocketServerProtocolHandler(webSocketPath, subprotocols, false, maxFrameLength));

        pipeline.addLast(new NewWebSocketFrameHandler(httpServer, req, user, maxDropped, waterMark));

        // Effectively trigger websocket-handler (will attempt handshake)
        nettyContext.fireChannelRead(req);
    }

    /**
     * Adapts Netty's pipeline for allowing WebSocket upgrade
     *
     * @param ctx
     *            context for this channel handler
     */
    private void prepareChannelForWebSocketUpgrade(ChannelHandlerContext ctx, HttpRequest req, String yamcsInstance,
            String processor, User user) {
        contentExpected = true;
        ctx.pipeline().addLast(new HttpObjectAggregator(65536));

        int maxFrameLength = wsConfig.getInt("maxFrameLength");
        int maxDropped = wsConfig.getInt("connectionCloseNumDroppedMsg");
        int lo = wsConfig.getConfig("writeBufferWaterMark").getInt("low");
        int hi = wsConfig.getConfig("writeBufferWaterMark").getInt("high");
        WriteBufferWaterMark waterMark = new WriteBufferWaterMark(lo, hi);

        // Add websocket-specific handlers to channel pipeline
        String webSocketPath = req.uri();
        String subprotocols = "json, protobuf";
        ctx.pipeline().addLast(new WebSocketServerProtocolHandler(webSocketPath, subprotocols, false, maxFrameLength));

        HttpRequestInfo originalRequestInfo = new HttpRequestInfo(req);
        originalRequestInfo.setYamcsInstance(yamcsInstance);
        originalRequestInfo.setProcessor(processor);
        originalRequestInfo.setUser(user);
        ctx.pipeline().addLast(new WebSocketFrameHandler(originalRequestInfo, maxDropped, waterMark));

        // Effectively trigger websocket-handler (will attempt handshake)
        ctx.fireChannelRead(req);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof NotSslRecordException) {
            log.info("Expected a TLS/SSL packet. Closing channel");
        } else {
            log.error("Closing channel: {}", cause.getMessage());
        }
        ctx.close();
    }

    public static <T extends Message> ChannelFuture sendMessageResponse(ChannelHandlerContext ctx, HttpRequest req,
            HttpResponseStatus status, T responseMsg) {
        ByteBuf body = ctx.alloc().buffer();
        MediaType contentType = getAcceptType(req);

        try {
            if (contentType == MediaType.PROTOBUF) {
                try (ByteBufOutputStream channelOut = new ByteBufOutputStream(body)) {
                    responseMsg.writeTo(channelOut);
                }
            } else if (contentType == MediaType.PLAIN_TEXT) {
                body.writeCharSequence(responseMsg.toString(), StandardCharsets.UTF_8);
            } else { // JSON by default
                contentType = MediaType.JSON;
                String str = JsonFormat.printer().preservingProtoFieldNames().print(responseMsg);
                body.writeCharSequence(str, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, e.toString());
        }
        HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, body);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType.toString());
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes());

        return sendResponse(ctx, req, response);
    }

    public static ChannelFuture sendPlainTextError(ChannelHandlerContext ctx, HttpRequest req,
            HttpResponseStatus status) {
        return sendPlainTextError(ctx, req, status, status.toString());
    }

    public static ChannelFuture sendPlainTextError(ChannelHandlerContext ctx, HttpRequest req,
            HttpResponseStatus status, String msg) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status,
                Unpooled.copiedBuffer(msg + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        return sendResponse(ctx, req, response);
    }

    public static ChannelFuture sendResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse response) {
        if (response.status() == HttpResponseStatus.OK) {
            log.info("{} {} {} {}", ctx.channel().id().asShortText(), req.method(), req.uri(),
                    response.status().code());
            ChannelFuture writeFuture = ctx.writeAndFlush(response);
            if (!HttpUtil.isKeepAlive(req)) {
                writeFuture.addListener(ChannelFutureListener.CLOSE);
            }
            return writeFuture;
        } else {
            if (req != null) {
                log.warn("{} {} {} {}", ctx.channel().id().asShortText(), req.method(), req.uri(),
                        response.status().code());
            } else {
                log.warn("{} malformed or illegal request. Sending back {}", ctx.channel().id().asShortText(),
                        response.status().code());
            }
            ChannelFuture writeFuture = ctx.writeAndFlush(response);
            writeFuture = writeFuture.addListener(ChannelFutureListener.CLOSE);
            return writeFuture;
        }
    }

    private void cleanPipeline(ChannelPipeline pipeline) {
        while (pipeline.last() != this) {
            pipeline.removeLast();
        }
    }

    /**
     * Returns the Accept header if present and not set to ANY or Content-Type header if present or JSON if none of the
     * headers is present or the Accept is present and set to ANY.
     */
    private static MediaType getAcceptType(HttpRequest req) {
        String acceptType = req.headers().get(HttpHeaderNames.ACCEPT);
        if (acceptType != null) {
            MediaType r = MediaType.from(acceptType);
            if (r == MediaType.ANY) {
                return getContentType(req);
            } else {
                return r;
            }
        } else {
            return getContentType(req);
        }
    }

    /**
     * @return The Content-Type header if present or else defaults to JSON.
     */
    public static MediaType getContentType(HttpRequest req) {
        String declaredContentType = req.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (declaredContentType != null) {
            return MediaType.from(declaredContentType);
        }
        return MediaType.JSON;
    }

    private String getAccessTokenFromCookie(HttpRequest req) {
        HttpHeaders headers = req.headers();
        if (headers.contains(HttpHeaderNames.COOKIE)) {
            Set<Cookie> cookies = ServerCookieDecoder.STRICT.decode(headers.get(HttpHeaderNames.COOKIE));
            for (Cookie c : cookies) {
                if ("access_token".equalsIgnoreCase(c.name())) {
                    return c.value();
                }
            }
        }
        return null;
    }

    private User handleBasicAuth(ChannelHandlerContext ctx, HttpRequest req) throws HttpException {
        String header = req.headers().get(HttpHeaderNames.AUTHORIZATION);
        String userpassEncoded = header.substring(AUTH_TYPE_BASIC.length());
        String userpassDecoded;
        try {
            userpassDecoded = new String(Base64.getDecoder().decode(userpassEncoded));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Could not decode Base64-encoded credentials");
        }

        // Username is not allowed to contain ':', but passwords are
        String[] parts = userpassDecoded.split(":", 2);
        if (parts.length < 2) {
            throw new BadRequestException("Malformed username/password (Not separated by colon?)");
        }

        try {
            AuthenticationToken token = new UsernamePasswordToken(parts[0], parts[1].toCharArray());
            AuthenticationInfo authenticationInfo = securityStore.login(token).get();
            return securityStore.getDirectory().getUser(authenticationInfo.getUsername());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AuthenticationException) {
                throw new UnauthorizedException(e.getCause().getMessage());
            } else {
                throw new InternalServerErrorException(e.getCause());
            }
        }
    }

    private User handleBearerAuth(ChannelHandlerContext ctx, HttpRequest req) throws UnauthorizedException {
        String header = req.headers().get(HttpHeaderNames.AUTHORIZATION);
        String accessToken = header.substring(AUTH_TYPE_BEARER.length());
        return handleAccessToken(ctx, req, accessToken);
    }

    private User handleAccessToken(ChannelHandlerContext ctx, HttpRequest req, String accessToken)
            throws UnauthorizedException {
        TokenStore tokenStore = httpServer.getTokenStore();
        AuthenticationInfo authenticationInfo = tokenStore.verifyAccessToken(accessToken);
        if (!securityStore.verifyValidity(authenticationInfo)) {
            tokenStore.revokeAccessToken(accessToken);
            throw new UnauthorizedException("Could not verify token");
        }

        return securityStore.getDirectory().getUser(authenticationInfo.getUsername());
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
