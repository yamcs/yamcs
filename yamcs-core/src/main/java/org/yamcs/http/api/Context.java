package org.yamcs.http.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

import org.yamcs.api.Api;
import org.yamcs.api.MediaType;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpRequestHandler;
import org.yamcs.logging.Log;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;

import com.google.protobuf.Descriptors.MethodDescriptor;
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

    private final Log log;

    /**
     * Unique id for this call.
     */
    private final String id;

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
    public final HttpRequest nettyRequest;

    private final Route route;

    private final Matcher regexMatch;

    /**
     * The request user.
     */
    public final User user;

    private long txSize = 0;
    private int statusCode;

    /**
     * A future that covers the full API call.
     * <p>
     * API implementations should use the passed {@link Observer} instead of this future.
     */
    final CompletableFuture<Void> requestFuture = new CompletableFuture<>();

    Context(ChannelHandlerContext nettyContext, HttpRequest nettyRequest, Route route, Matcher regexMatch) {
        this.id = "R" + counter.incrementAndGet();
        this.nettyContext = nettyContext;
        this.nettyRequest = nettyRequest;
        this.route = route;
        this.regexMatch = regexMatch;
        this.user = nettyContext.channel().attr(HttpRequestHandler.CTX_USER).get();

        log = new Log(Context.class);
        log.setContext(id);

        route.incrementRequestCount();

        // Track status for metric purposes
        requestFuture.whenComplete((channelFuture, e) -> {
            if (e != null) {
                log.debug("API request finished with error: {}, transferred bytes: {}", e.getMessage(), txSize);
            } else {
                log.debug("API request finished successfully, transferred bytes: {}", txSize);
            }

            if (statusCode == 0) {
                log.warn("{}: Status code not reported", this);
            } else if (statusCode < 200 || statusCode >= 300) {
                route.incrementErrorCount();
            }
        });
    }

    boolean isDone() {
        return requestFuture.isDone();
    }

    public Api<Context> getApi() {
        return route.getApi();
    }

    public MethodDescriptor getMethod() {
        String methodName = route.getDescriptor().getMethod();
        return getApi().getDescriptorForType().findMethodByName(methodName);
    }

    public boolean isServerStreaming() {
        return getMethod().toProto().getServerStreaming();
    }

    public boolean isClientStreaming() {
        return getMethod().toProto().getClientStreaming();
    }

    public Message getRequestPrototype() {
        MethodDescriptor method = getMethod();
        return route.getApi().getRequestPrototype(method);
    }

    public Message getResponsePrototype() {
        MethodDescriptor method = getMethod();
        return route.getApi().getResponsePrototype(method);
    }

    public String getBodySpecifier() {
        return route.getBody();
    }

    public int getMaxBodySize() {
        return route.getMaxBodySize();
    }

    public String getURI() {
        return nettyRequest.uri();
    }

    /**
     * Get the number of bytes transferred as the result of this call. It should not include the http headers. Note that
     * the number might be increased before the data is sent so it will be wrong if there was an error sending data.
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

    public boolean hasRouteParam(String name) {
        try {
            return regexMatch.group(name) != null;
        } catch (IllegalArgumentException e) {
            // Could likely be improved, we need this catch in case of multiple @Route annotations
            // for the same method. Because then above call could throw an error if the requested
            // group is not present in one of the patterns
            return false;
        }
    }

    public String getRouteParam(String name) {
        return regexMatch.group(name);
    }

    public boolean hasBody() {
        return HttpUtil.getContentLength(nettyRequest) > 0;
    }

    public boolean isOffloaded() {
        return route.isOffloaded();
    }

    /**
     * Deserializes the incoming message extracted from the body. This does not care about what the HTTP method is. Any
     * required checks should be done elsewhere.
     * <p>
     * This method is only able to read JSON or Protobuf, the two auto-supported serialization mechanisms. If a certain
     * operation needs to read anything else, it should check for that itself, and then use
     * {@link #getBodyAsInputStream()}.
     */
    public <T extends Message.Builder> T getBodyAsMessage(T builder) throws BadRequestException {
        MediaType sourceContentType = deriveSourceContentType();
        // Allow for empty body, otherwise user has to specify '{}'
        if (HttpUtil.getContentLength(nettyRequest) > 0) {
            if (MediaType.PROTOBUF.equals(sourceContentType)) {
                try (InputStream cin = getBodyAsInputStream()) {
                    builder.mergeFrom(cin);
                } catch (IOException e) {
                    throw new BadRequestException(e);
                }
            } else {
                try {
                    String json = getBody().toString(StandardCharsets.UTF_8);
                    JsonFormat.parser().merge(json, builder);
                } catch (InvalidProtocolBufferException e) {
                    throw new BadRequestException(e);
                }
            }
        }
        return builder;
    }

    public InputStream getBodyAsInputStream() {
        return new ByteBufInputStream(getBody());
    }

    /**
     * returns the body of the http request
     * 
     */
    public ByteBuf getBody() {
        if (nettyRequest instanceof FullHttpRequest) {
            return ((FullHttpRequest) nettyRequest).content();
        } else {
            throw new IllegalArgumentException("Can only provide body of a FullHttpRequest");
        }
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

    public void checkSystemPrivilege(SystemPrivilege privilege) throws ForbiddenException {
        if (!user.hasSystemPrivilege(privilege)) {
            throw new ForbiddenException("No system privilege '" + privilege + "'");
        }
    }

    public void checkObjectPrivileges(ObjectPrivilegeType type, Collection<String> objects) throws ForbiddenException {
        checkObjectPrivileges(type, objects.toArray(new String[objects.size()]));
    }

    public void checkObjectPrivileges(ObjectPrivilegeType type, String... objects) throws ForbiddenException {
        for (String object : objects) {
            if (!user.hasObjectPrivilege(type, object)) {
                throw new ForbiddenException("No " + type + " authorization for '" + object + "'");
            }
        }
    }

    @Override
    public String toString() {
        return id;
    }
}
