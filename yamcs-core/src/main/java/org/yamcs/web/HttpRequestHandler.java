package org.yamcs.web;

import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
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

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.protobuf.MessageLite;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

/**
 * Handles handshakes and messages.
 * 
 * We have following different request types - static requests - sent to the
 * fileRequestHandler - do no go higher in the netty pipeline - websocket
 * requests - the pipeline is modified to add the websocket handshaker. - load
 * data requests - the pipeline is modified by the respective route handler -
 * standard API calls (the vast majority) - the HttpObjectAgreggator is added
 * upstream to collect (and limit) all data from the http request in one object.
 * 
 * Because we support multiple http requests on one connection (keep-alive), we
 * have to clean the pipeline when the request type changes
 */
public class HttpRequestHandler extends ChannelInboundHandlerAdapter {

    private static final String STATIC_PATH = "_static";
    private static final String API_PATH = "api";

    public static final AttributeKey<ChunkedTransferStats> CTX_CHUNK_STATS = AttributeKey.valueOf("chunkedTransferStats");
    public static final AttributeKey<AuthenticationToken> CTX_AUTH_TOKEN = AttributeKey.valueOf("authToken");

    private static final Logger log = LoggerFactory.getLogger(HttpRequestHandler.class);

    public static final Object CONTENT_FINISHED_EVENT = new Object();
    private static StaticFileHandler fileRequestHandler = new StaticFileHandler();
    private Router apiRouter;
    private boolean contentExpected = false;
    
    private static JsonFactory jsonFactory = new JsonFactory();
    
    private static final FullHttpResponse BAD_REQUEST = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST,
            Unpooled.EMPTY_BUFFER);
    static {
        HttpUtil.setContentLength(BAD_REQUEST, 0);
    }
    public static final byte[] NEWLINE_BYTES = "\r\n".getBytes();
    
    public HttpRequestHandler(Router apiRouter) {
        this.apiRouter = apiRouter;
    }
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)  throws Exception {
        if (msg instanceof HttpRequest) {
            contentExpected = false;
            processRequest(ctx, (HttpRequest) msg);
            ReferenceCountUtil.release(msg);
        } else if (msg instanceof HttpContent) {
            if (contentExpected) {
                ctx.fireChannelRead(msg);
                if (msg instanceof LastHttpContent) {
                    ctx.fireUserEventTriggered(CONTENT_FINISHED_EVENT);
                }
            } else if (!(msg instanceof LastHttpContent)) {
                log.warn("Unexpected http content received: {}", msg);
                ReferenceCountUtil.release(msg);
                ctx.close();
            }
        } else {
            log.error("Unexpected message received: {}", msg);
            ReferenceCountUtil.release(msg);
        }
    }

    private void processRequest(ChannelHandlerContext ctx, HttpRequest req) {
        // We have this also on info level coupled with the HTTP response status
        // code,
        // but this is on debug for an earlier reporting while debugging issues
        log.debug("{} {}", req.method(), req.uri());

        Privilege priv = Privilege.getInstance();

        if (priv.isEnabled()) {
            ctx.channel().attr(CTX_AUTH_TOKEN).set(null);
            priv.authenticateHttp(ctx, req).whenComplete((authToken, t) -> {
                if (t != null) {
                    t = unwindThrowable(t);
                    if (t instanceof AuthorizationPendingException) {
                        return;
                    } else {
                        sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
                    }
                } else {
                    ctx.channel().attr(CTX_AUTH_TOKEN).set(authToken);
                    handleRequest(authToken, ctx, req);
                }
            });

        } else {
            handleRequest(null, ctx, req);
        }
    }

    private Throwable unwindThrowable(Throwable t) {
        while (((t instanceof ExecutionException) || (t instanceof CompletionException) || (t instanceof UncheckedExecutionException)) 
                && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private void handleRequest(AuthenticationToken authToken, ChannelHandlerContext ctx, HttpRequest req) {
     
        try {
            cleanPipeline(ctx.pipeline());
            // Decode URI, to correctly ignore query strings in path handling
            QueryStringDecoder qsDecoder = new QueryStringDecoder(req.uri());
            String[] path = qsDecoder.path().split("/", 3); // path starts with / so path[0] is always empty
            switch (path[1]) {
            case STATIC_PATH:
                if (path.length == 2) { // do not accept "/_static/" (i.e. directory listing) requests
                    sendPlainTextError(ctx, req, FORBIDDEN);
                    return;
                }
                fileRequestHandler.handleStaticFileRequest(ctx, req, path[2]);
                return;
            case API_PATH:
                contentExpected = apiRouter.scheduleExecution(ctx, req, qsDecoder);
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
                        // Everything else is handled by angular's router
                        // (enables deep linking in html5 mode)
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
     * 
     * @param ctx
     *  context for this channel handler
     */
    private void prepareChannelForWebSocketUpgrade(ChannelHandlerContext ctx, HttpRequest req, String yamcsInstance, AuthenticationToken authToken) {
        contentExpected = true;
        ctx.pipeline().addLast(new HttpObjectAggregator(65536));

        // Add websocket-specific handlers to channel pipeline
        String webSocketPath = req.uri();
        ctx.pipeline().addLast(new WebSocketServerProtocolHandler(webSocketPath));

        HttpRequestInfo originalRequestInfo = new HttpRequestInfo(req);
        originalRequestInfo.setYamcsInstance(yamcsInstance);
        originalRequestInfo.setAuthenticationToken(authToken);
        ctx.pipeline().addLast(new WebSocketFrameHandler(originalRequestInfo));

        // Effectively trigger websocket-handler (will attempt handshake)
        ctx.fireChannelRead(req);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Will close channel due to exception", cause);
        ctx.close();
    }

    public ChannelFuture sendRedirect(ChannelHandlerContext ctx, HttpRequest req, String newUri) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().set(HttpHeaderNames.LOCATION, newUri);
        log.info("{} {} {}", req.method(), req.uri(), HttpResponseStatus.FOUND.code());
        return ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    public static <T extends MessageLite> ChannelFuture sendMessageResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponseStatus status, T responseMsg, Schema<T> responseSchema) {
            return sendMessageResponse(ctx, req, status, responseMsg, responseSchema, true);
    }
    
    public static <T extends MessageLite> ChannelFuture sendMessageResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponseStatus status, T responseMsg, Schema<T> responseSchema, boolean autoCloseOnError) {
        ByteBuf body = ctx.alloc().buffer();
        MediaType contentType = MediaType.getAcceptType(req);
        
        try (ByteBufOutputStream channelOut = new ByteBufOutputStream(body)){
            if (contentType == MediaType.PROTOBUF) {
                responseMsg.writeTo(channelOut);
            } else if (contentType == MediaType.PLAIN_TEXT) {
                channelOut.write(responseMsg.toString().getBytes(StandardCharsets.UTF_8));
            } else { //JSON by default
                contentType = MediaType.JSON;
                JsonGenerator generator = jsonFactory.createGenerator(channelOut, JsonEncoding.UTF8);
                JsonIOUtil.writeTo(generator, responseMsg, responseSchema, false);
                generator.close();
                body.writeBytes(NEWLINE_BYTES); // For curl comfort
            }
        } catch (IOException e) {
            return sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, e.toString());
        }
        byte[] dst = new byte[body.readableBytes()];
        body.markReaderIndex();
        body.readBytes(dst);
        body.resetReaderIndex();
        HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, body);
        HttpUtils.setContentTypeHeader(response, contentType);

        int txSize =  body.readableBytes();
        HttpUtil.setContentLength(response, txSize);
        return sendResponse(ctx, req, response, autoCloseOnError);
    }
    
    public static ChannelFuture sendPlainTextError(ChannelHandlerContext ctx, HttpRequest req, HttpResponseStatus status) {
        return sendPlainTextError(ctx, req, status, status.toString());
    }

    public static ChannelFuture sendPlainTextError(ChannelHandlerContext ctx, HttpRequest req, HttpResponseStatus status, String msg) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, Unpooled.copiedBuffer(msg + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        return sendResponse(ctx, req, response, true);
    }
    
    public static ChannelFuture sendResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse response, boolean autoCloseOnError) {
        if(response.status()==HttpResponseStatus.OK) {
            log.info("{} {} {}", req.method(), req.uri(), response.status().code());
            ChannelFuture writeFuture = ctx.writeAndFlush(response);
            if (!HttpUtil.isKeepAlive(req)) {
                writeFuture.addListener(ChannelFutureListener.CLOSE);
            }
            return writeFuture;
        } else {
            if (req != null) {
                log.warn("{} {} {}", req.method(), req.uri(), response.status().code());
            } else {
                log.warn("Malformed or illegal request. Sending back {}",  response.status().code());
            }
            ChannelFuture writeFuture = ctx.writeAndFlush(response);
            if(autoCloseOnError) {
                writeFuture = writeFuture.addListener(ChannelFutureListener.CLOSE);
            } 
            return writeFuture;
        }
    }

    private void cleanPipeline(ChannelPipeline pipeline) {
        while (pipeline.last() != this) {
            pipeline.removeLast();
        }
    }

    /**
     * Sends base HTTP response indicating the use of chunked transfer encoding
     */
    public static ChannelFuture startChunkedTransfer(ChannelHandlerContext ctx, HttpRequest req, MediaType contentType, String filename) {
        log.info("{} {} 200 Starting chunked transfer", req.method(), req.uri());
        ctx.channel().attr(CTX_CHUNK_STATS).set(new ChunkedTransferStats(req.method(), req.uri()));
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);

        // Set Content-Disposition header so that supporting clients will treat
        // response as a downloadable file
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
