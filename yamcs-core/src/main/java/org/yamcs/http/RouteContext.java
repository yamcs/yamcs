package org.yamcs.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

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

public class RouteContext extends Context {

    /**
     * The Netty HTTP request for an RPC call. In general RPC implementations should avoid using this object. It is
     * exposed only because we need it for some HTTP-specific functionalities that are not covered by our RPC
     * implementation (e.g. multipart uploading)
     */
    public final HttpRequest nettyRequest;
    public FullHttpRequest fullNettyRequest;

    private Route route;
    private Matcher regexMatch;

    RouteContext(HttpServer httpServer, ChannelHandlerContext nettyContext, User user, HttpRequest nettyRequest,
            Route route, Matcher regexMatch) {
        super(httpServer, nettyContext, user, route.getApi());
        this.nettyRequest = nettyRequest;
        this.route = route;
        this.regexMatch = regexMatch;

        route.incrementRequestCount();

        // Track status for metric purposes
        requestFuture.whenComplete((channelFuture, e) -> {
            if (e != null) {
                log.debug("API call finished with error: {}, transferred bytes: {}", e.getMessage(), txSize);
            } else {
                log.debug("API call finished successfully, transferred bytes: {}", txSize);
            }

            if (statusCode == 0) {
                log.warn("{}: Status code not reported", this);
            } else if (statusCode < 200 || statusCode >= 300) {
                route.incrementErrorCount();
            }
        });
    }

    void setFullNettyRequest(FullHttpRequest fullNettyRequest) {
        this.fullNettyRequest = fullNettyRequest;
    }

    @Override
    public MethodDescriptor getMethod() {
        String methodName = route.getDescriptor().getMethod();
        return api.getDescriptorForType().findMethodByName(methodName);
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
        MediaType sourceContentType = HttpRequestHandler.getContentType(nettyRequest);
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
        if (fullNettyRequest != null) {
            return fullNettyRequest.content();
        } else {
            throw new IllegalArgumentException("Can only provide body of a FullHttpRequest");
        }
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
