package org.yamcs.http.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.api.MediaType;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpRequestHandler;
import org.yamcs.http.api.Router.RouteMatch;
import org.yamcs.security.User;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;

/**
 * Request context used in RPC-style endpoints.
 */
public class Context {

    private static AtomicInteger counter = new AtomicInteger();

    /**
     * Unique id for this call.
     */
    public final int id;

    /**
     * The Netty request context for an RPC call. In general RPC implementation should avoid using this object. It is
     * exposed only because we need it for some HTTP-specific functionalities that are not covered by our RPC
     * implementation (e.g. http chunking)
     */
    public final ChannelHandlerContext nettyContext;

    /**
     * The Netty HTTP request for an RPC call. In general RPC implementations should avoid using this object. It is
     * exposed only because we need it for some HTTP-specific functionalities that are not covered by our RPC
     * implementation (e.g. multipart uploading)
     */
    public final FullHttpRequest nettyRequest;

    /**
     * The request user.
     */
    public final User user;

    private long txSize = 0;
    private RouteMatch routeMatch;
    private int statusCode;

    /**
     * A future that covers the full API call.
     * <p>
     * API implementations should use the passed {@link Observer} instead of this future.
     */
    CompletableFuture<Void> requestFuture = new CompletableFuture<>();

    Context(ChannelHandlerContext nettyContext, FullHttpRequest nettyRequest, User user) {
        this.id = counter.incrementAndGet();
        this.nettyContext = nettyContext;
        this.nettyRequest = nettyRequest;
        this.user = user;
    }

    /**
     * Get the number of bytes transferred as the result of the REST call. It should not include the http headers. Note
     * that the number might be increased before the data is sent so it will be wrong if there was an error sending
     * data.
     * 
     * 
     * @return number of bytes transferred as part of the request
     */
    public long getTransferredSize() {
        return txSize;
    }

    public void addTransferredSize(long byteCount) {
        txSize += byteCount;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void reportStatusCode(int statusCode) {
        // TODO It should be possible to refactor this such that
        // the HTTP status code becomes the result of the requestFuture.
        if (this.statusCode != 0) {
            throw new IllegalArgumentException("Status code already set to " + this.statusCode);
        }
        this.statusCode = statusCode;
    }

    public String getClientAddress() {
        InetSocketAddress address = (InetSocketAddress) nettyContext.channel().remoteAddress();
        return address.getAddress().getHostAddress();
    }

    RouteMatch getRouteMatch() {
        return routeMatch;
    }

    void setRouteMatch(RouteMatch routeMatch) {
        this.routeMatch = routeMatch;
    }

    public boolean hasRouteParam(String name) {
        try {
            return routeMatch.regexMatch.group(name) != null;
        } catch (IllegalArgumentException e) {
            // Could likely be improved, we need this catch in case of multiple @Route annotations
            // for the same method. Because then above call could throw an error if the requested
            // group is not present in one of the patterns
            return false;
        }
    }

    public String getRouteParam(String name) {
        return routeMatch.getRouteParam(name);
    }

    public boolean hasBody() {
        return HttpUtil.getContentLength(nettyRequest) > 0;
    }

    /**
     * Deserializes the incoming message extracted from the body. This does not care about what the HTTP method is. Any
     * required checks should be done elsewhere.
     * <p>
     * This method is only able to read JSON or Protobuf, the two auto-supported serialization mechanisms. If a certain
     * operation needs to read anything else, it should check for that itself, and then use
     * {@link #bodyAsInputStream()}.
     */
    public <T extends Message.Builder> T bodyAsMessage(T builder) throws BadRequestException {
        MediaType sourceContentType = deriveSourceContentType();
        // Allow for empty body, otherwise user has to specify '{}'
        if (HttpUtil.getContentLength(nettyRequest) > 0) {
            if (MediaType.PROTOBUF.equals(sourceContentType)) {
                try (InputStream cin = bodyAsInputStream()) {
                    builder.mergeFrom(cin);
                } catch (IOException e) {
                    throw new BadRequestException(e);
                }
            } else {
                try {
                    String json = nettyRequest.content().toString(StandardCharsets.UTF_8);
                    JsonFormat.parser().merge(json, builder);
                } catch (InvalidProtocolBufferException e) {
                    throw new BadRequestException(e);
                }
            }
        }
        return builder;
    }

    public InputStream bodyAsInputStream() {
        return new ByteBufInputStream(nettyRequest.content());
    }

    /**
     * returns the body of the http request
     * 
     */
    public ByteBuf getRequestContent() {
        return nettyRequest.content();
    }

    /**
     * @return see {@link HttpRequestHandler#getContentType(HttpRequest)}
     */
    public MediaType deriveSourceContentType() {
        return HttpRequestHandler.getContentType(nettyRequest);
    }

    public MediaType deriveTargetContentType() {
        return deriveTargetContentType(nettyRequest);
    }

    /**
     * Derives an applicable content type for the output. This tries to match JSON or BINARY media types with the ACCEPT
     * header, else it will revert to the (derived) source content type.
     *
     * @return the content type that will be used for the response message
     */
    public static MediaType deriveTargetContentType(HttpRequest httpRequest) {
        MediaType mt = MediaType.JSON;
        if (httpRequest.headers().contains(HttpHeaderNames.ACCEPT)) {
            String acceptedContentType = httpRequest.headers().get(HttpHeaderNames.ACCEPT);
            mt = MediaType.from(acceptedContentType);
        } else if (httpRequest.headers().contains(HttpHeaderNames.CONTENT_TYPE)) {
            String declaredContentType = httpRequest.headers().get(HttpHeaderNames.CONTENT_TYPE);
            mt = MediaType.from(declaredContentType);
        }

        // we only support one of these two for the output, so just force JSON by default
        if (mt != MediaType.JSON && mt != MediaType.PROTOBUF) {
            mt = MediaType.JSON;
        }
        return mt;
    }
}
