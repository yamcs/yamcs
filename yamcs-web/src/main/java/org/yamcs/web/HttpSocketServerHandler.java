package org.yamcs.web;

import static org.jboss.netty.handler.codec.http.HttpHeaders.*;
import static org.jboss.netty.handler.codec.http.HttpMethod.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.*;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.regex.Pattern;


import org.codehaus.jackson.JsonFactory;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles handshakes and messages
 */
public class HttpSocketServerHandler extends SimpleChannelUpstreamHandler {
    //the request to get the list of displays goes here
    public static final String DISPLAYS_PATH = "displays";
    public static final String STATIC_PATH = "_static";
    
    final static Logger log=LoggerFactory.getLogger(HttpSocketServerHandler.class.getName());

    //this checks the url to be of shape /yamcs-instance/handler/...
    final Pattern urlPattern=Pattern.compile("\\/([\\w\\-]+)\\/([\\w\\-]*)\\/(.*)");
    
    JsonFactory jsonFactory=new JsonFactory();
    
    //these two are valid after the socket has been upgraded
    Channel channel;
    ParameterClient paraClient;
    static StaticFileRequestHandler fileRequestHandler=new StaticFileRequestHandler();
    static DisplayRequestHandler displayRequestHandler=new DisplayRequestHandler(fileRequestHandler);
    WebSocketServerHandler webSocketHandler= new WebSocketServerHandler();
   
    
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (msg instanceof HttpRequest) {
            handleHttpRequest(ctx, (HttpRequest) msg, e);
        } else if (msg instanceof WebSocketFrame) {
            webSocketHandler.handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest req, MessageEvent e) throws Exception {
        log.debug("{} {}", req.getMethod(), req.getUri());

        // Allow only GET methods.
        if (req.getMethod() != GET) {
            sendHttpResponse(ctx, req, new DefaultHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }
    
        if (req.getUri().equals("favicon.ico")) { //TODO send the sugarcube
            HttpResponse res = new DefaultHttpResponse(HTTP_1_1, NOT_FOUND);
            sendHttpResponse(ctx, req, res);
            return;
        }
        String uri;
        
        try {
            uri = URLDecoder.decode(req.getUri(), "UTF-8");
        } catch (UnsupportedEncodingException e1) {
            log.warn("Cannot decode uri", e1);
            sendHttpResponse(ctx, req, new DefaultHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }
        String[] path=uri.split("/",3); //uri starts with / so path[0] is always empty
        if(path.length==1) {
            HttpResponse res = new DefaultHttpResponse(HTTP_1_1, NOT_FOUND);
            sendHttpResponse(ctx, req, res);
            return;
        }
        if(STATIC_PATH.equals(path[1])) {
            if(path.length==2) { //do not accept "/_static/" (i.e. directory listing) requests 
                HttpResponse res = new DefaultHttpResponse(HTTP_1_1, FORBIDDEN);
                sendHttpResponse(ctx, req, res);
                return;
            }
            fileRequestHandler.handleStaticFileRequest(ctx, req, e, path[2]);
            return;
        }
        
        String yamcsInstance=path[1];

        if(!HttpSocketServer.getInstance().isInstanceRegistered(yamcsInstance)) {
            HttpResponse res = new DefaultHttpResponse(HTTP_1_1, NOT_FOUND);
            sendHttpResponse(ctx, req, res);
            return;
        }
        if((path.length==2) || path[2].isEmpty() || path[2].equals("index.html")) {
            fileRequestHandler.handleStaticFileRequest(ctx, req, e, "index.html");
            return;
        }
        
        String[] rpath = path[2].split("/",2);
        String handler=rpath[0];
        if(WebSocketServerHandler.WEBSOCKET_PATH.equals(handler)) {
            webSocketHandler.handleHttpRequest(ctx, req, e, yamcsInstance);
            return;
        }
        
        if(DISPLAYS_PATH.equals(handler)) {
            displayRequestHandler.handleRequest(ctx, req, e, yamcsInstance, path.length>1? rpath[1] : null);
        } else {
            HttpResponse res = new DefaultHttpResponse(HTTP_1_1, NOT_FOUND);
            sendHttpResponse(ctx, req, res);
            return;
        }
    }
    
    private void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res) {
        // Generate an error page if response status code is not OK (200).
        if (res.getStatus().getCode() != 200) {
            res.setContent(ChannelBuffers.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8));
            setContentLength(res, res.getContent().readableBytes());
        }

        // Send the response and close the connection if necessary.
        ChannelFuture f = ctx.getChannel().write(res);
        if (!isKeepAlive(req) || res.getStatus().getCode() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        log.warn("caught exception ChannelHandlerContext: "+ctx, e);
        e.getCause().printStackTrace();
        e.getChannel().close();
    }
    
    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        webSocketHandler.channelDisconnected(e.getChannel());
    }
}
