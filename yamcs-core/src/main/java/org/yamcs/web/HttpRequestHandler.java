package org.yamcs.web;

import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.api.MediaType;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.AuthorizationPendingException;
import org.yamcs.security.Privilege;
import org.yamcs.web.rest.Router;
import org.yamcs.web.websocket.WebSocketFrameHandler;

import com.google.common.util.concurrent.UncheckedExecutionException;

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
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;


/**
 * Handles handshakes and messages
 */
public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final String STATIC_PATH = "_static";
    private static final String API_PATH = "api";

    public static final AttributeKey<ChunkedTransferStats> CTX_CHUNK_STATS = AttributeKey.valueOf("chunkedTransferStats");

    private static final Logger log = LoggerFactory.getLogger(HttpRequestHandler.class);

    private static StaticFileHandler fileRequestHandler = new StaticFileHandler();
    private Router apiRouter;

    public HttpRequestHandler(Router apiRouter) {
        this.apiRouter = apiRouter;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        // We have this also on info level coupled with the HTTP response status code,
        // but this is on debug for an earlier reporting while debugging issues
        log.debug("{} {}", req.getMethod(), req.getUri());

        Privilege priv = Privilege.getInstance();

        if (priv.isEnabled()) {
            priv.authenticateHttp(ctx, req)
            .whenComplete((authToken, t) -> {
                if(t!=null) {
                    t = unwindThrowable(t);
                    if(t instanceof AuthorizationPendingException) {
                        return;
                    } else {
                        sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
                    }
                } else {
                    handleRequest(authToken, ctx, req);    
                }
                
            });
            
           
        } else {
            handleRequest(null, ctx, req);
        }
    }
    
    private Throwable unwindThrowable(Throwable t) {
        while(((t instanceof ExecutionException)||(t instanceof CompletionException)||(t instanceof UncheckedExecutionException))
                && t.getCause()!=null) {
            t = t.getCause();
        }
        return t;
    }
    private void handleRequest(AuthenticationToken authToken, ChannelHandlerContext ctx, FullHttpRequest req) {
        try {
            // Decode URI, to correctly ignore query strings in path handling
            QueryStringDecoder qsDecoder = new QueryStringDecoder(req.getUri());
            String[] path = qsDecoder.path().split("/", 3); // path starts with / so path[0] is always empty
            switch (path[1]) {
            case STATIC_PATH:
                if (path.length == 2) { //do not accept "/_static/" (i.e. directory listing) requests
                    sendPlainTextError(ctx, req, FORBIDDEN);
                    return;
                }
                fileRequestHandler.handleStaticFileRequest(ctx, req, path[2]);
                return;
            case API_PATH:
                apiRouter.handleHttpRequest(ctx, req, authToken, qsDecoder);
                return;
            case "":
                // overview of all instances
                fileRequestHandler.handleStaticFileRequest(ctx, req, "_site/index.html");
                return;
            default:
                String yamcsInstance = path[1];
                if (!YamcsServer.hasInstance(yamcsInstance)) {
                    sendPlainTextError(ctx, req, NOT_FOUND);
                    return;
                }
                if (path.length > 2) {
                    String[] rpath = path[2].split("/", 2);
                    String handler = rpath[0];
                    if (WebSocketFrameHandler.WEBSOCKET_PATH.equals(handler)) {
                        prepareChannelForWebSocketUpgrade(ctx, req, yamcsInstance, authToken);
                        return;
                    } else {
                        // Everything else is handled by angular's router (enables deep linking in html5 mode)
                        fileRequestHandler.handleStaticFileRequest(ctx, req, "_site/instance.html");
                    }
                } else {
                    fileRequestHandler.handleStaticFileRequest(ctx, req, "_site/instance.html");
                }
            }
        } catch (IOException e) {
            log.warn("Exception while handling http request", e);
            sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Adapts Netty's pipeline for allowing WebSocket upgrade
     * @param ctx context for this channel handler
     */
    private void prepareChannelForWebSocketUpgrade(ChannelHandlerContext ctx, FullHttpRequest req, String yamcsInstance, AuthenticationToken authToken) {

        // Add websocket-specific handlers to channel pipeline
        String webSocketPath = req.getUri();
        ctx.pipeline().addLast(new WebSocketServerProtocolHandler(webSocketPath));

        HttpRequestInfo originalRequestInfo = new HttpRequestInfo(req);
        originalRequestInfo.setYamcsInstance(yamcsInstance);
        originalRequestInfo.setAuthenticationToken(authToken);
        ctx.pipeline().addLast(new WebSocketFrameHandler(originalRequestInfo));

        // Effectively trigger websocket-handler (will attempt handshake)
        ctx.fireChannelRead(req.retain());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Will close channel due to exception", cause);
        ctx.close();
    }


    public ChannelFuture sendRedirect(ChannelHandlerContext ctx, HttpRequest req, String newUri) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().set(HttpHeaders.Names.LOCATION, newUri);
        log.info("{} {} {}", req.getMethod(), req.getUri(), HttpResponseStatus.FOUND.code());
        return ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }



    public static ChannelFuture sendPlainTextError(ChannelHandlerContext ctx, HttpRequest req, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, status, Unpooled.copiedBuffer(status + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
        return sendError(ctx, req, response);
    }

    public static ChannelFuture sendError(ChannelHandlerContext ctx, HttpRequest req, HttpResponse response) {
        if (req != null) {
            log.warn("{} {} {}", req.getMethod(), req.getUri(), response.getStatus().code());
        } else {
            log.warn("Malformed or illegal request. Sending back " + response.getStatus().code());
        }

        return ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    public static ChannelFuture sendOK(ChannelHandlerContext ctx, HttpRequest req, HttpResponse response) {
        log.info("{} {} {}", req.getMethod(), req.getUri(), response.getStatus().code());
        ChannelFuture writeFuture = ctx.writeAndFlush(response);

        if (!HttpHeaders.isKeepAlive(req)) {
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
        return writeFuture;
    }

    /**
     * Sends base HTTP response indicating the use of chunked transfer encoding
     */
    public static ChannelFuture startChunkedTransfer(ChannelHandlerContext ctx, HttpRequest req, MediaType contentType, String filename) {
        log.info("{} {} 200 Starting chunked transfer", req.getMethod(), req.getUri());
        ctx.attr(CTX_CHUNK_STATS).set(new ChunkedTransferStats(req.getMethod(), req.getUri()));
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
        response.headers().set(Names.CONTENT_TYPE, contentType);

        // Set Content-Disposition header so that supporting clients will treat response as a downloadable file
        if (filename != null) {
            response.headers().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        }
        return ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    public static ChannelFuture writeChunk(ChannelHandlerContext ctx, ByteBuf buf) throws IOException {
        Channel ch = ctx.channel();
        if (!ch.isOpen()) {
            throw new ClosedChannelException();
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

    public static class ChunkedTransferStats {
        public int totalBytes = 0;
        public int chunkCount = 0;
        HttpMethod originalMethod;
        String originalUri;

        public ChunkedTransferStats(HttpMethod method, String uri) {
            originalMethod = method;
            originalUri = uri;
        }
    }
}
