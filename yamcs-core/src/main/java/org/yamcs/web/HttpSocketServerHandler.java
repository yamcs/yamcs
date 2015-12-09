package org.yamcs.web;

import static io.netty.handler.codec.http.HttpHeaders.setHeader;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
import org.yamcs.security.UsernamePasswordToken;
import org.yamcs.time.SimulationTimeService;
import org.yamcs.web.rest.ClientRequestHandler;
import org.yamcs.web.rest.DisplayRequestHandler;
import org.yamcs.web.rest.InstanceRequestHandler;
import org.yamcs.web.rest.LinkRequestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequestHandler;
import org.yamcs.web.rest.UserRequestHandler;
import org.yamcs.web.rest.archive.ArchiveRequestHandler;
import org.yamcs.web.rest.mdb.MDBRequestHandler;
import org.yamcs.web.rest.processor.ProcessorRequestHandler;
import org.yamcs.web.websocket.WebSocketServerHandler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;


/**
 * Handles handshakes and messages
 */
public class HttpSocketServerHandler extends SimpleChannelInboundHandler<Object> {

    public static final String STATIC_PATH = "_static";
    public static final String API_PATH = "api";

    final static Logger log = LoggerFactory.getLogger(HttpSocketServerHandler.class);

    static StaticFileRequestHandler fileRequestHandler = new StaticFileRequestHandler();
    Map<String, RestRequestHandler> restHandlers = new HashMap<>();
    WebSocketServerHandler webSocketHandler = new WebSocketServerHandler();
    
    public HttpSocketServerHandler() {
        restHandlers.put("archive", new ArchiveRequestHandler());
        restHandlers.put("clients", new ClientRequestHandler());
        restHandlers.put("displays",  new DisplayRequestHandler());
        restHandlers.put("instances", new InstanceRequestHandler());
        restHandlers.put("links", new LinkRequestHandler());
        restHandlers.put("mdb", new MDBRequestHandler());
        restHandlers.put("processors", new ProcessorRequestHandler());
        restHandlers.put("simTime", new SimulationTimeService.SimTimeRequestHandler());
        restHandlers.put("user", new UserRequestHandler());
    }
    
    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            webSocketHandler.handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        } else {
            log.info("Sending bad request error for message {}", msg);
            sendError(ctx, null, BAD_REQUEST);
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
                    sendError(ctx, req, BAD_REQUEST);
                    return;
                }
                // Get encoded user and password, comes after "Basic "
                String userpassEncoded = authorizationHeader.substring(6);
                String userpassDecoded  = new String(Base64.getDecoder().decode(userpassEncoded));

                // Username is not allowed to contain ':', but passwords are
                String[] parts = userpassDecoded.split(":", 2);
                if (parts.length < 2) {
                    sendError(ctx, req, BAD_REQUEST);
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
                sendError(ctx, req, FORBIDDEN);
                return;
            }
            fileRequestHandler.handleStaticFileRequest(ctx, req, path[2]);
            return;
        case API_PATH:
            if (path.length == 2 || "".equals(path[2])) {
                sendError(ctx, req, FORBIDDEN);
                return;
            }
            
            RestRequest restReq = AbstractRequestHandler.toRestRequest(ctx, req, qsDecoder, authToken);
            
            String resource = restReq.getPathSegment(2);
            RestRequestHandler restHandler = restHandlers.get(resource);
            if (restHandler != null) {
                restHandler.handleRequestOrError(restReq, 3);
            } else {
                sendError(ctx, req, NOT_FOUND);
            }
            return;
        case "":
            // overview of all instances 
            fileRequestHandler.handleStaticFileRequest(ctx, req, "_site/index.html");
            return;
        default:
            String yamcsInstance = path[1];
            if (!HttpSocketServer.getInstance().isInstanceRegistered(yamcsInstance)) {
                sendError(ctx, req, NOT_FOUND);
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
    
    private void sendRedirect(ChannelHandlerContext ctx, HttpRequest req, String newUri) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().set(HttpHeaders.Names.LOCATION, newUri);
        log.info("{} {} {}", req.getMethod(), req.getUri(), HttpResponseStatus.FOUND.code());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    
    private void sendUnauthorized(ChannelHandlerContext ctx, HttpRequest req) {
        ByteBuf buf = Unpooled.copiedBuffer(HttpResponseStatus.UNAUTHORIZED.toString() + "\r\n", CharsetUtil.UTF_8);
        
        HttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.UNAUTHORIZED, buf);
        setHeader(res, HttpHeaders.Names.WWW_AUTHENTICATE, "Basic realm=\"" + Privilege.getRealmName() + "\"");
        log.warn("{} {} {} [realm=\"{}\"]", req.getMethod(), req.getUri(), res.getStatus().code(), Privilege.getRealmName());
        ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }
    
    private static void sendError(ChannelHandlerContext ctx, HttpRequest req, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, status, Unpooled.copiedBuffer(status + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
        
        if (req != null) {
            log.warn("{} {} {}", req.getMethod(), req.getUri(), status.code());
        } else {
            log.warn("Malformed or illegal request. Sending back " + status.code());
        }

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
