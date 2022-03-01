package org.yamcs.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.yamcs.security.User;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.FieldMaskUtil;
import com.google.protobuf.util.JsonFormat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;

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

    private int maxBodySize;
    private String fieldMaskRoot;

    RouteContext(HttpServer httpServer, ChannelHandlerContext nettyContext, User user, HttpRequest nettyRequest,
            Route route, Matcher regexMatch) {
        super(httpServer, nettyContext, user, route.getApi());
        this.nettyRequest = nettyRequest;
        this.route = route;
        this.regexMatch = regexMatch;
        maxBodySize = Math.max(httpServer.getConfig().getInt("maxContentLength"), route.getMaxBodySize());

        route.incrementRequestCount();

        fieldMaskRoot = route.getFieldMaskRoot();
        if (fieldMaskRoot == null) {
            // If the response message looks like a list response, use the convention
            // that the fieldmask applies to each repeated resource message.
            Descriptor responseDescriptor = getResponsePrototype().getDescriptorForType();
            if (responseDescriptor.getName().startsWith("List")) {
                List<FieldDescriptor> repeatedFields = responseDescriptor.getFields().stream()
                        .filter(f -> f.isRepeated())
                        .collect(Collectors.toList());
                if (repeatedFields.size() == 1) {
                    fieldMaskRoot = repeatedFields.get(0).getName();
                }
            }
        }

        // Consider FieldMask, for response filtering
        QueryStringDecoder qsDecoder = new QueryStringDecoder(nettyRequest.uri());
        List<String> fieldsParameter = qsDecoder.parameters().get("fields");
        if (fieldsParameter != null && !fieldsParameter.isEmpty()) {
            fieldMask = FieldMaskUtil.fromString(fieldsParameter.get(0));
        } else {
            String fieldsHeader = nettyRequest.headers().get("x-yamcs-fields");
            if (fieldsHeader != null) {
                fieldMask = FieldMaskUtil.fromString(fieldsHeader);
            }
        }

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
    public String getClientAddress() {
        String forwardedFor = nettyRequest.headers().get("x-forwarded-for");
        if (forwardedFor != null) {
            return forwardedFor;
        } else {
            return super.getClientAddress();
        }
    }

    @Override
    public MethodDescriptor getMethod() {
        String methodName = route.getDescriptor().getMethod();
        return api.getDescriptorForType().findMethodByName(methodName);
    }

    public String getBodySpecifier() {
        return route.getBody();
    }

    public String getFieldMaskRoot() {
        return fieldMaskRoot;
    }

    public int getMaxBodySize() {
        return maxBodySize;
    }

    public String getURI() {
        return nettyRequest.uri();
    }

    public boolean hasRouteParam(String name) {
        try {
            return regexMatch.group(name) != null;
        } catch (IllegalArgumentException e) {
            // Could likely be improved, we need this catch in case of multiple bindings
            // for the same method. Because then above call could throw an error if the
            // requested group is not present in one of the patterns
            return false;
        }
    }

    public String getRouteParam(String name) {
        String routeParam = regexMatch.group(name);
        if (routeParam == null) {
            return null;
        }
        try {
            return URLDecoder.decode(routeParam, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    public boolean hasBody() {
        return HttpUtil.getContentLength(nettyRequest) > 0;
    }

    public boolean isOffloaded() {
        return route.isOffloaded();
    }

    public String getLogFormat() {
        return route.getLogFormat();
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
