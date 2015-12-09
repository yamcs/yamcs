package org.yamcs.web;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.setHeader;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.MediaType;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
import org.yamcs.security.UsernamePasswordToken;
import org.yamcs.time.SimulationTimeService;
import org.yamcs.web.rest.ClientRestHandler;
import org.yamcs.web.rest.DisplayRestHandler;
import org.yamcs.web.rest.InstanceRestHandler;
import org.yamcs.web.rest.LinkRestHandler;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.UserRestHandler;
import org.yamcs.web.rest.archive.ArchiveRestHandler;
import org.yamcs.web.rest.mdb.MDBRestHandler;
import org.yamcs.web.rest.processor.ProcessorRestHandler;
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
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;


/**
 * Handles handshakes and messages
 */
public class HttpServerHandler extends SimpleChannelInboundHandler<Object> {

    public static final String STATIC_PATH = "_static";
    public static final String API_PATH = "api";

    final static Logger log = LoggerFactory.getLogger(HttpServerHandler.class);

    static StaticFileHandler fileRequestHandler = new StaticFileHandler();
    Map<String, RestHandler> restHandlers = new HashMap<>();
    WebSocketServerHandler webSocketHandler = new WebSocketServerHandler();
    
    public HttpServerHandler() {
        restHandlers.put("archive", new ArchiveRestHandler());
        restHandlers.put("clients", new ClientRestHandler());
        restHandlers.put("displays",  new DisplayRestHandler());
        restHandlers.put("instances", new InstanceRestHandler());
        restHandlers.put("links", new LinkRestHandler());
        restHandlers.put("mdb", new MDBRestHandler());
        restHandlers.put("processors", new ProcessorRestHandler());
        restHandlers.put("simTime", new SimulationTimeService.SimTimeRestHandler());
        restHandlers.put("user", new UserRestHandler());
    }
    
    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            webSocketHandler.handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        } else {
            log.info("Sending bad request error for message {}", msg);
            sendPlainTextError(ctx, null, BAD_REQUEST);
            return;
        }
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        webSocketHandler.channelDisconnected(ctx.channel());
        super.channelInactive(ctx);
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        // We have this also on info level coupled with the HTTP response status code, but this is on debug
        // for an earlier reporting while debugging issues
        log.debug("{} {}", req.getMethod(), req.getUri());
        
        QueryStringDecoder qsDecoder = new QueryStringDecoder(req.getUri());
        String uri = qsDecoder.path(); // URI without Query String

        AuthenticationToken authToken = null;
        if (Privilege.getInstance().isEnabled()) {
            if (req.headers().contains(HttpHeaders.Names.AUTHORIZATION)) {
                String authorizationHeader = req.headers().get(HttpHeaders.Names.AUTHORIZATION);
                if (!authorizationHeader.startsWith("Basic ")) { // Exact case only
                    sendPlainTextError(ctx, req, BAD_REQUEST);
                    return;
                }
                // Get encoded user and password, comes after "Basic "
                String userpassEncoded = authorizationHeader.substring(6);
                String userpassDecoded  = new String(Base64.getDecoder().decode(userpassEncoded));

                // Username is not allowed to contain ':', but passwords are
                String[] parts = userpassDecoded.split(":", 2);
                if (parts.length < 2) {
                    sendPlainTextError(ctx, req, BAD_REQUEST);
                    return;
                }
                authToken = new UsernamePasswordToken(parts[0], parts[1]);
                if (!Privilege.getInstance().authenticates(authToken)) {
                    sendUnauthorized(ctx, req);
                    return;
                }
            } else {
                sendUnauthorized(ctx, req);
                return;
            }
        }
        
        String[] path = uri.split("/", 3); //uri starts with / so path[0] is always empty
        switch (path[1]) {
        case STATIC_PATH:
            if (path.length == 2) { //do not accept "/_static/" (i.e. directory listing) requests 
                sendPlainTextError(ctx, req, FORBIDDEN);
                return;
            }
            fileRequestHandler.handleStaticFileRequest(ctx, req, path[2]);
            return;
        case API_PATH:
            if (path.length == 2 || "".equals(path[2])) {
                sendPlainTextError(ctx, req, FORBIDDEN);
                return;
            }
            
            RestRequest restReq = RouteHandler.toRestRequest(ctx, req, qsDecoder, authToken);
            
            String resource = restReq.getPathSegment(2);
            RestHandler restHandler = restHandlers.get(resource);
            if (restHandler != null) {
                restHandler.handleRequestOrError(restReq, 3);
            } else {
                sendPlainTextError(ctx, req, NOT_FOUND);
            }
            return;
        case "":
            // overview of all instances 
            fileRequestHandler.handleStaticFileRequest(ctx, req, "_site/index.html");
            return;
        default:
            String yamcsInstance = path[1];
            if (!HttpServer.getInstance().isInstanceRegistered(yamcsInstance)) {
                sendPlainTextError(ctx, req, NOT_FOUND);
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
    
    public ChannelFuture sendRedirect(ChannelHandlerContext ctx, HttpRequest req, String newUri) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().set(HttpHeaders.Names.LOCATION, newUri);
        log.info("{} {} {}", req.getMethod(), req.getUri(), HttpResponseStatus.FOUND.code());
        return ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    
    private ChannelFuture sendUnauthorized(ChannelHandlerContext ctx, HttpRequest req) {
        ByteBuf buf = Unpooled.copiedBuffer(HttpResponseStatus.UNAUTHORIZED.toString() + "\r\n", CharsetUtil.UTF_8);
        
        HttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.UNAUTHORIZED, buf);
        setHeader(res, HttpHeaders.Names.WWW_AUTHENTICATE, "Basic realm=\"" + Privilege.getRealmName() + "\"");
        log.warn("{} {} {} [realm=\"{}\"]", req.getMethod(), req.getUri(), res.getStatus().code(), Privilege.getRealmName());
        return ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
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
        if (!isKeepAlive(req)) {
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
        return writeFuture;
    }
    
    /**
     * Sends base HTTP response indicating that we'll use chunked transfer encoding
     */
    public static ChannelFuture startChunkedTransfer(ChannelHandlerContext ctx, HttpRequest req, MediaType contentType) {
        log.info("{} {} 200 Starting chunked transfer", req.getMethod(), req.getUri());
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
        response.headers().set(Names.CONTENT_TYPE, contentType);
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
                log.warn("Channel open, but not writable. Waiting it out for max 10 seconds.");
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
    public static void stopChunkedTransfer(ChannelHandlerContext ctx, HttpRequest req, ChannelFuture lastChunkFuture) {
        if (lastChunkFuture != null) {
            lastChunkFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        writeEmptyLastContent(ctx, req);
                    } else {
                        log.error("Last chunk was not written successfully. Closing channel without sending empty final chunk", future.cause());
                        ctx.channel().close();
                    }
                }
            });
        } else {
            writeEmptyLastContent(ctx, req);
        }
    }
    
    private static ChannelFuture writeEmptyLastContent(ChannelHandlerContext ctx, HttpRequest req) {
        // TODO Should probably only output this info on successful write of the empty_last_content
        log.info("{} {} --- Finished chunked transfer", req.getMethod(), req.getUri());
        ChannelFuture chunkWriteFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        return chunkWriteFuture.addListener(ChannelFutureListener.CLOSE);
    }
}
