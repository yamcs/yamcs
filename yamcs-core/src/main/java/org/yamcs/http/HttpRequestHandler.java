package org.yamcs.http;

import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLHandshakeException;

import org.yamcs.logging.Log;

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
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
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

    public static final AttributeKey<String> CTX_CONTEXT_PATH = AttributeKey.valueOf("contextPath");
    public static final AttributeKey<HttpRequest> CTX_HTTP_REQUEST = AttributeKey.valueOf("httpRequest");
    public static final AttributeKey<String> CTX_USERNAME = AttributeKey.valueOf("username");
    public static final AttributeKey<RouteContext> CTX_CONTEXT = AttributeKey.valueOf("routeContext");

    private static final Log log = new Log(HttpRequestHandler.class);

    public static final Object CONTENT_FINISHED_EVENT = new Object();

    private HttpServer httpServer;
    private String contextPath;

    public HttpRequestHandler(HttpServer httpServer) {
        this.httpServer = httpServer;
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
                log.warn("{} Exception while decoding HTTP message: {}", ctx.channel().id().asShortText(), dr.cause());
                sendPlainTextError(ctx, null, HttpResponseStatus.BAD_REQUEST);
                return;
            }
        }

        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;

            // We have this also on info level coupled with the HTTP response status
            // code, but this is on debug for an earlier reporting while debugging issues
            log.debug("{} {} {}", ctx.channel().id().asShortText(), req.method(), req.uri());

            try {
                handleRequest(ctx, req);
            } catch (InternalServerErrorException e) {
                log.error(req.uri(), e);
                sendPlainTextError(ctx, req, e.getStatus(), e.getMessage());
            } catch (HttpException e) {
                log.warn("{}: {}", req.uri(), e.getMessage());
                sendPlainTextError(ctx, req, e.getStatus(), e.getMessage());
            } catch (Throwable t) {
                log.error(req.uri(), t);
                sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }

            ReferenceCountUtil.release(msg);
        } else if (msg instanceof HttpContent) {
            ctx.fireChannelRead(msg);
            if (msg instanceof LastHttpContent) {
                ctx.fireUserEventTriggered(CONTENT_FINISHED_EVENT);
            }
        } else {
            log.error("{} unexpected message received: {}", ctx.channel().id().asShortText(), msg);
            ReferenceCountUtil.release(msg);
        }
    }

    private void handleRequest(ChannelHandlerContext ctx, HttpRequest req) throws IOException {
        cleanPipeline(ctx.pipeline());
        ctx.channel().attr(CTX_CONTEXT_PATH).set(contextPath);
        ctx.channel().attr(CTX_HTTP_REQUEST).set(req);
        ctx.channel().attr(CTX_CONTEXT).set(null); // Cleanup in case of keep-alive
        ctx.channel().attr(CTX_USERNAME).set(null); // Cleanup in case of keep-alive

        if (!req.uri().startsWith(contextPath)) {
            sendPlainTextError(ctx, req, NOT_FOUND);
            return;
        }

        String pathString = HttpUtils.getPathWithoutContext(req, contextPath);

        // Note: pathString starts with / so path[0] is always empty
        String[] path = pathString.split("/", 3);
        String pathComponent = path.length >= 2 ? path[1] : "";

        var handler = httpServer.createHandler(pathComponent);
        if (handler != null) {
            ctx.pipeline().addLast(new HttpContentCompressor());
            ctx.pipeline().addLast(new HttpObjectAggregator(65536));
            ctx.pipeline().addLast(handler);
            ctx.fireChannelRead(req);
            return;
        }

        var httpHandler = httpServer.createHttpHandler(pathComponent);
        if (httpHandler == null) {
            httpHandler = httpServer.createHttpHandler(ANY_PATH);
        }
        if (httpHandler != null) {
            httpHandler.handle(ctx, req);
            return;
        }

        // Too bad.
        sendPlainTextError(ctx, req, NOT_FOUND);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        String channelId = ctx.channel().id().asShortText();
        if (cause instanceof NotSslRecordException) {
            log.info("{} Closing channel: expected a TLS/SSL packet", channelId);
        } else if (cause instanceof IOException && cause.getMessage().contains("reset by peer")) {
            // Java 11: Unclean client close. Don't care about stack trace
            log.trace("{} Closing channel: {}", channelId, cause.getMessage());
        } else if (cause instanceof SocketException && cause.getMessage().equals("Connection reset")) {
            // Java 17: Unclean client close. Don't care about stack trace
            log.trace("{} Closing channel: {}", channelId, cause.getMessage());
        } else if (cause instanceof DecoderException
                && ((DecoderException) cause).getCause() instanceof SSLHandshakeException) {
            // Very common when using Chrome and unknown certificates. Don't care about stack trace
            log.debug("{} Closing channel: {}", channelId, cause.getMessage());
        } else {
            log.error("{} Closing channel: {}", channelId, cause.getMessage(), cause);
        }
        ctx.close();
    }

    public static <T extends Message> ChannelFuture sendMessageResponse(ChannelHandlerContext ctx, HttpRequest req,
            HttpResponseStatus status, T responseMsg) {
        // Note: don't use this method when there's a possibility of JSON/Any message serialization
        // The used JSON printer does not have type definitions registered.

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
        response.headers().set(CONTENT_TYPE, contentType.toString());
        response.headers().set(CONTENT_LENGTH, body.readableBytes());

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
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
        return sendResponse(ctx, req, response);
    }

    public static ChannelFuture sendResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse response) {
        int status = response.status().code();
        boolean keepAlive = HttpUtil.isKeepAlive(req);

        if (100 <= status && status < 400) { // Information, Success, or Redirection
            log.info("{} {} {} {}", ctx.channel().id().asShortText(), req.method(), req.uri(), status);
        } else { // Client error or server error
            keepAlive = false;
            if (req != null) {
                log.warn("{} {} {} {}", ctx.channel().id().asShortText(), req.method(), req.uri(), status);
            } else {
                log.warn("{} malformed or illegal request. Sending back {}", ctx.channel().id().asShortText(), status);
            }
        }

        if (keepAlive) {
            response.headers().set(CONNECTION, KEEP_ALIVE);
            return ctx.channel().writeAndFlush(response);
        } else {
            response.headers().set(CONNECTION, CLOSE);
            ChannelFuture writeFuture = ctx.channel().writeAndFlush(response);
            return writeFuture.addListener(ChannelFutureListener.CLOSE);
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
    static MediaType getAcceptType(HttpRequest req) {
        String acceptType = req.headers().get(ACCEPT);
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
        String declaredContentType = req.headers().get(CONTENT_TYPE);
        if (declaredContentType != null) {
            return MediaType.from(declaredContentType);
        }
        return MediaType.JSON;
    }
}
