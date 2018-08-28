package org.yamcs.web;

import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.api.MediaType;
import org.yamcs.security.SecurityStore;
import org.yamcs.security.User;
import org.yamcs.web.rest.Router;
import org.yamcs.web.websocket.WebSocketFrameHandler;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMessage;
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

/**
 * Handles handshakes and messages.
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

    private static final String API_PATH = "api";
    private static final String AUTH_PATH = "auth";
    private static final String STATIC_PATH = "static";

    public static final AttributeKey<ChunkedTransferStats> CTX_CHUNK_STATS = AttributeKey
            .valueOf("chunkedTransferStats");
    public static final AttributeKey<User> CTX_USER = AttributeKey.valueOf("user");

    private static final Logger log = LoggerFactory.getLogger(HttpRequestHandler.class);

    public static final Object CONTENT_FINISHED_EVENT = new Object();
    private static StaticFileHandler fileRequestHandler = new StaticFileHandler();
    private Router apiRouter;
    private AuthHandler authHandler = new AuthHandler();
    private boolean contentExpected = false;

    private static final FullHttpResponse BAD_REQUEST = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST,
            Unpooled.EMPTY_BUFFER);
    public static final String HANDLER_NAME_COMPRESSOR = "hndl_compressor";
    public static final String HANDLER_NAME_CHUNKED_WRITER = "hndl_chunked_writer";

    private static HttpAuthorizationChecker authChecker = new HttpAuthorizationChecker();
    static {
        HttpUtil.setContentLength(BAD_REQUEST, 0);
    }
    public static final byte[] NEWLINE_BYTES = "\r\n".getBytes();

    WebSocketConfig wsConfig;

    public HttpRequestHandler(Router apiRouter, WebSocketConfig wsConfig) {
        this.apiRouter = apiRouter;
        this.wsConfig = wsConfig;
    }

    public static HttpAuthorizationChecker getAuthorizationChecker() {
        return authChecker;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpMessage) {
            DecoderResult dr = ((HttpMessage) msg).decoderResult();
            if (!dr.isSuccess()) {
                log.warn("{} got exception decoding http message: {}", ctx.channel().id().asShortText(), dr.cause());
                ctx.writeAndFlush(BAD_REQUEST);
                return;
            }
        }

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
                log.warn("{} unexpected http content received: {}", ctx.channel().id().asShortText(), msg);
                ReferenceCountUtil.release(msg);
                ctx.close();
            }
        } else {
            log.error("{} unexpected message received: {}", ctx.channel().id().asShortText(), msg);
            ReferenceCountUtil.release(msg);
        }
    }

    private void processRequest(ChannelHandlerContext ctx, HttpRequest req) {
        // We have this also on info level coupled with the HTTP response status
        // code, but this is on debug for an earlier reporting while debugging issues
        log.debug("{} {} {}", ctx.channel().id().asShortText(), req.method(), req.uri());

        try {
            handleRequest(ctx, req);
        } catch (IOException e) {
            log.warn("Exception while handling http request", e);
            sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        } catch (HttpException e) {
            sendPlainTextError(ctx, req, e.getStatus(), e.getMessage());
        }
    }

    private void verifyAuthentication(ChannelHandlerContext ctx, HttpRequest req)
            throws HttpException {
        SecurityStore security = SecurityStore.getInstance();
        if (security.isEnabled()) {
            User user = authChecker.verifyAuth(ctx, req);
            ctx.channel().attr(CTX_USER).set(user);
        } else {
            User user = security.getUnauthenticatedUser();
            ctx.channel().attr(CTX_USER).set(user);
        }
    }

    private void handleRequest(ChannelHandlerContext ctx, HttpRequest req)
            throws IOException, HttpException {
        cleanPipeline(ctx.pipeline());

        // Decode URI, to correctly ignore query strings in path handling
        QueryStringDecoder qsDecoder = new QueryStringDecoder(req.uri());
        String[] path = qsDecoder.path().split("/", 3); // path starts with / so path[0] is always empty
        switch (path[1]) {
        case STATIC_PATH:
            if (path.length == 2) { // do not accept "/static/" (i.e. directory listing) requests
                sendPlainTextError(ctx, req, FORBIDDEN);
                return;
            }
            fileRequestHandler.handleStaticFileRequest(ctx, req, path[2]);
            return;
        case AUTH_PATH:
            ctx.pipeline().addLast(HttpRequestHandler.HANDLER_NAME_COMPRESSOR, new HttpContentCompressor());
            ctx.pipeline().addLast(new HttpObjectAggregator(65536));
            ctx.pipeline().addLast(authHandler);
            ctx.fireChannelRead(req);
            contentExpected = true;
            return;
        case API_PATH:
            verifyAuthentication(ctx, req);
            contentExpected = apiRouter.scheduleExecution(ctx, req, qsDecoder);
            return;
        case WebSocketFrameHandler.WEBSOCKET_PATH:
            verifyAuthentication(ctx, req);
            if (path.length == 2) { // An instance should be specified
                sendPlainTextError(ctx, req, FORBIDDEN);
                return;
            }
            if (YamcsServer.hasInstance(path[2])) {
                prepareChannelForWebSocketUpgrade(ctx, req, path[2]);
            } else {
                sendPlainTextError(ctx, req, NOT_FOUND);
            }
            return;
        default:
            // Everything else is handled client-side by the Angular router (HTML5 deep-linking)
            fileRequestHandler.handleStaticFileRequest(ctx, req, "index.html");
        }
    }

    /**
     * Adapts Netty's pipeline for allowing WebSocket upgrade
     *
     * @param ctx
     *            context for this channel handler
     */
    private void prepareChannelForWebSocketUpgrade(ChannelHandlerContext ctx, HttpRequest req, String yamcsInstance) {
        contentExpected = true;
        ctx.pipeline().addLast(new HttpObjectAggregator(65536));

        // Add websocket-specific handlers to channel pipeline
        String webSocketPath = req.uri();
        String subprotocols = "json, protobuf";
        ctx.pipeline().addLast(new WebSocketServerProtocolHandler(webSocketPath, subprotocols, false,
                wsConfig.getMaxFrameLength()));

        HttpRequestInfo originalRequestInfo = new HttpRequestInfo(req);
        originalRequestInfo.setYamcsInstance(yamcsInstance);
        originalRequestInfo.setUser(ctx.channel().attr(CTX_USER).get());
        ctx.pipeline().addLast(new WebSocketFrameHandler(originalRequestInfo,
                wsConfig.getConnectionCloseNumDroppedMsg(), wsConfig.getWriteBufferWaterMark()));

        // Effectively trigger websocket-handler (will attempt handshake)
        ctx.fireChannelRead(req);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Will close channel due to exception", cause);
        ctx.close();
    }

    public static ChannelFuture sendRedirect(ChannelHandlerContext ctx, HttpRequest req, String newUri) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().set(HttpHeaderNames.LOCATION, newUri);
        log.info("{} {} {} {}", ctx.channel().id().asShortText(), req.method(), req.uri(),
                HttpResponseStatus.FOUND.code());
        return ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    public static <T extends Message> ChannelFuture sendMessageResponse(ChannelHandlerContext ctx, HttpRequest req,
            HttpResponseStatus status, T responseMsg) {
        return sendMessageResponse(ctx, req, status, responseMsg, true);
    }

    public static <T extends Message> ChannelFuture sendMessageResponse(ChannelHandlerContext ctx, HttpRequest req,
            HttpResponseStatus status, T responseMsg, boolean autoCloseOnError) {
        ByteBuf body = ctx.alloc().buffer();
        MediaType contentType = MediaType.getAcceptType(req);

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
                body.writeBytes(NEWLINE_BYTES); // For curl comfort
            }
        } catch (IOException e) {
            return sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, e.toString());
        }
        HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, body);
        HttpUtils.setContentTypeHeader(response, contentType);

        int txSize = body.readableBytes();
        HttpUtil.setContentLength(response, txSize);

        return sendResponse(ctx, req, response, autoCloseOnError);
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
        return sendResponse(ctx, req, response, true);
    }

    public static ChannelFuture sendResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse response,
            boolean autoCloseOnError) {
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
            if (autoCloseOnError) {
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
     * Sends base HTTP response indicating the use of chunked transfer encoding NM 11-May-2018: We do not put the
     * ChunckedWriteHandler on the pipeline because the input is already chunked.
     * 
     */
    public static ChannelFuture startChunkedTransfer(ChannelHandlerContext ctx, HttpRequest req, MediaType contentType,
            String filename) {
        log.info("{} {} {} 200 starting chunked transfer", ctx.channel().id().asShortText(), req.method(), req.uri());
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
                boolean writeCompleted = writeFuture.await(10, TimeUnit.SECONDS);
                if (!writeCompleted) {
                    throw new IOException("Channel did not become writable in 10 seconds");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
