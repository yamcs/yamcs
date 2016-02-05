package org.yamcs.web;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.setHeader;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.MediaType;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
import org.yamcs.security.UsernamePasswordToken;
import org.yamcs.web.rest.Router;
import org.yamcs.web.websocket.WebSocketServerHandler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;


/**
 * Handles handshakes and messages
 */
public class HttpHandler extends SimpleChannelInboundHandler<Object> {

    private static final String STATIC_PATH = "_static";
    private static final String API_PATH = "api";
    
    public static final AttributeKey<HttpRequest> CTX_HTTP_REQUEST = AttributeKey.valueOf("request");
    public static final AttributeKey<ChunkedTransferStats> CTX_CHUNK_STATS = AttributeKey.valueOf("chunkedTransferStats");
    public static final AttributeKey<String> CTX_ATTACHMENT_NAME = AttributeKey.valueOf("attachmentName");

    private static final Logger log = LoggerFactory.getLogger(HttpHandler.class);

    private static StaticFileHandler fileRequestHandler = new StaticFileHandler();
    private Router apiRouter;
    private WebSocketServerHandler webSocketHandler = new WebSocketServerHandler();    
    
    public HttpHandler(Router apiRouter) {
        this.apiRouter = apiRouter;
    }
    
    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            webSocketHandler.handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        } else {
            log.warn("Received garbage message {}", msg);
            sendPlainTextError(ctx, BAD_REQUEST);
            return;
        }
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        webSocketHandler.channelDisconnected(ctx.channel());
        super.channelInactive(ctx);
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        // We have this also on info level coupled with the HTTP response status code,
        // but this is on debug for an earlier reporting while debugging issues
        log.debug("{} {}", req.getMethod(), req.getUri());

        AuthenticationToken authToken = null;
        if (Privilege.getInstance().isEnabled()) {
            try {
                authToken = authenticate(ctx, req);
            } catch (BadRequestException e) {
                sendPlainTextError(ctx, BAD_REQUEST);
                return;
            } catch (UnauthorizedException e) {
                sendUnauthorized(ctx, req);
                return;
            }
        }
        
        // Store in context, mainly for use by full-response loggers
        ctx.attr(CTX_HTTP_REQUEST).set(req);
        
        String[] path = req.getUri().split("/", 3); //uri starts with / so path[0] is always empty
        switch (path[1]) {
        case STATIC_PATH:
            if (path.length == 2) { //do not accept "/_static/" (i.e. directory listing) requests 
                sendPlainTextError(ctx, FORBIDDEN);
                return;
            }
            fileRequestHandler.handleStaticFileRequest(ctx, req, path[2]);
            return;
        case API_PATH:
            apiRouter.handleHttpRequest(ctx, req, authToken);
            return;
        case "":
            // overview of all instances
            fileRequestHandler.handleStaticFileRequest(ctx, req, "_site/index.html");
            return;
        default:
            String yamcsInstance = path[1];
            if (!HttpServer.getInstance().isInstanceRegistered(yamcsInstance)) {
                sendPlainTextError(ctx, NOT_FOUND);
                return;
            }
            
            if (path.length > 2) {
                String[] rpath = path[2].split("/", 2);
                String handler = rpath[0];
                if (WebSocketServerHandler.WEBSOCKET_PATH.equals(handler)) {
                    webSocketHandler.handleHttpRequest(ctx, req, yamcsInstance, authToken);
                } else {
                    // Everything else is handled by angular's router (enables deep linking in html5 mode)
                    fileRequestHandler.handleStaticFileRequest(ctx, req, "_site/instance.html");                
                }
            } else {
                fileRequestHandler.handleStaticFileRequest(ctx, req, "_site/instance.html");
            }
            return;
        }
    }
    
    private AuthenticationToken authenticate(ChannelHandlerContext ctx, HttpRequest req) throws BadRequestException, UnauthorizedException {
        if (!req.headers().contains(HttpHeaders.Names.AUTHORIZATION)) {
            throw new UnauthorizedException("Authorization required, but nothing provided");
        }
        
        String authorizationHeader = req.headers().get(HttpHeaders.Names.AUTHORIZATION);
        if (!authorizationHeader.startsWith("Basic ")) { // Exact case only
            throw new BadRequestException("Unsupported Authorization header '" + authorizationHeader + "'");
        }
        // Get encoded user and password, comes after "Basic "
        String userpassEncoded = authorizationHeader.substring(6);
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
        AuthenticationToken token = new UsernamePasswordToken(parts[0], parts[1]);
        if (!Privilege.getInstance().authenticates(token)) {
            throw new UnauthorizedException();
        }
        return token;
    }
    
    public ChannelFuture sendRedirect(ChannelHandlerContext ctx, String newUri) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().set(HttpHeaders.Names.LOCATION, newUri);
        HttpRequest request = ctx.attr(CTX_HTTP_REQUEST).get();
        log.info("{} {} {}", request.getMethod(), request.getUri(), HttpResponseStatus.FOUND.code());
        return ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    
    private ChannelFuture sendUnauthorized(ChannelHandlerContext ctx, HttpRequest request) {
        ByteBuf buf = Unpooled.copiedBuffer(HttpResponseStatus.UNAUTHORIZED.toString() + "\r\n", CharsetUtil.UTF_8);
        
        HttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.UNAUTHORIZED, buf);
        setHeader(res, HttpHeaders.Names.WWW_AUTHENTICATE, "Basic realm=\"" + Privilege.getRealmName() + "\"");
        
        log.warn("{} {} {} [realm=\"{}\"]", request.getMethod(), request.getUri(), res.getStatus().code(), Privilege.getRealmName());
        return ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }
    
    public static ChannelFuture sendPlainTextError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, status, Unpooled.copiedBuffer(status + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
        return sendError(ctx, response);
    }
    
    public static ChannelFuture sendError(ChannelHandlerContext ctx, HttpResponse response) {
        HttpRequest request = ctx.attr(CTX_HTTP_REQUEST).get();
        if (request != null) {
            log.warn("{} {} {}", request.getMethod(), request.getUri(), response.getStatus().code());
        } else {
            log.warn("Malformed or illegal request. Sending back " + response.getStatus().code());
        }

        return ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    
    public static ChannelFuture sendOK(ChannelHandlerContext ctx, HttpResponse response) {
        HttpRequest request = ctx.attr(CTX_HTTP_REQUEST).get();
        log.info("{} {} {}", request.getMethod(), request.getUri(), response.getStatus().code());
        ChannelFuture writeFuture = ctx.writeAndFlush(response);
        
        if (!isKeepAlive(request)) {
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
        return writeFuture;
    }
    
    /**
     * Sends base HTTP response indicating the use of chunked transfer encoding
     */
    public static ChannelFuture startChunkedTransfer(ChannelHandlerContext ctx, MediaType contentType) {
        HttpRequest request = ctx.attr(CTX_HTTP_REQUEST).get();
        log.info("{} {} 200 Starting chunked transfer", request.getMethod(), request.getUri());
        ctx.attr(CTX_CHUNK_STATS).set(new ChunkedTransferStats());
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
        response.headers().set(Names.CONTENT_TYPE, contentType);
        
        // Set Content-Disposition header so that supporting clients will treat response as a downloadable file
        if (ctx.attr(CTX_ATTACHMENT_NAME).get() != null) {
            String filename = ctx.attr(CTX_ATTACHMENT_NAME).get();
            response.headers().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        }
        return ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }
    
    public static ChannelFuture writeChunk(ChannelHandlerContext ctx, ByteBuf buf) throws IOException {
        Channel ch = ctx.channel();
        if (!ch.isOpen()) {
            throw new IOException("Channel not or no longer open");
        }
        ChannelFuture writeFuture = ctx.writeAndFlush(new DefaultHttpContent(buf));
        try {
            if (!ch.isWritable()) {
                log.warn("Channel open, but not writable. Waiting it out for max 10 seconds");
                boolean writeCompleted = writeFuture.await(10, TimeUnit.SECONDS);
                if (!writeCompleted) {
                    throw new IOException("Channel did not become writable in 10 seconds");
                }
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for channel to become writable", e);
            throw new IOException(e);
        }
        return writeFuture;
    }
    
    /**
     * Send empty chunk downstream to signal succesful end of response.
     * <p>
     * If lastChunkFuture is not null, the 'successful stop' of the chunked transfer will only be
     * written out when that future returns succes.
     */
    public static void stopChunkedTransfer(ChannelHandlerContext ctx, ChannelFuture lastChunkFuture) {
        if (lastChunkFuture != null) {
            lastChunkFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        writeEmptyLastContent(ctx);
                    } else {
                        log.error("Last chunk was not written successfully. Closing channel without sending empty final chunk", future.cause());
                        ctx.channel().close();
                    }
                }
            });
        } else {
            writeEmptyLastContent(ctx);
        }
    }
    
    private static ChannelFuture writeEmptyLastContent(ChannelHandlerContext ctx) {
        HttpRequest request = ctx.attr(CTX_HTTP_REQUEST).get();
        ChunkedTransferStats stats = ctx.attr(CTX_CHUNK_STATS).get();
        log.info("{} {} --- Finished chunked transfer ({} B)",
                request.getMethod(), request.getUri(), stats.totalBytes);
        ChannelFuture chunkWriteFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        return chunkWriteFuture.addListener(ChannelFutureListener.CLOSE);
    }
    
    public static class ChunkedTransferStats {
        public int totalBytes = 0;
        public int chunkCount = 0;
    }
}
