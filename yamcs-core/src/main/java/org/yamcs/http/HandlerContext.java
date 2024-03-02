package org.yamcs.http;

import static io.netty.handler.codec.http.HttpHeaderNames.ALLOW;
import static io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpHeaderNames.LOCATION;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_HTML;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.security.User;
import org.yamcs.templating.ParseException;
import org.yamcs.templating.TemplateProcessor;
import org.yamcs.utils.Mimetypes;

import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.protobuf.Message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.handler.ssl.SslHandler;

public class HandlerContext {

    private static final Mimetypes MIME = Mimetypes.getInstance();

    private final String contextPath;
    private final ChannelHandlerContext nettyContext;
    private final HttpRequest nettyRequest;
    private final QueryStringDecoder qsDecoder;
    private final User user;

    private Map<String, String> formParameters;

    public HandlerContext(String contextPath, ChannelHandlerContext ctx, HttpRequest req, User user) {
        this.contextPath = contextPath;
        nettyContext = ctx;
        nettyRequest = req;
        this.user = user;
        qsDecoder = new QueryStringDecoder(req.uri());
    }

    public User getUser() {
        return user;
    }

    /**
     * Attempts to derive the externally used URL to Yamcs based on request information
     * 
     * @return a url of the form [protocol]://[host]:[port][context]
     */
    public String getRequestBaseURL() {
        boolean tls = nettyContext.channel().pipeline().get(SslHandler.class) != null;
        String forwardedProto = nettyRequest.headers().get("x-forwarded-proto");
        if ("https".equals(forwardedProto)) {
            tls = true;
        }

        String host;
        int port = tls ? 443 : 80;

        String hostURL = nettyRequest.headers().get("x-forwarded-host");
        if (hostURL == null) {
            hostURL = nettyRequest.headers().get(HOST);
        }

        if (hostURL != null) {
            int idx = hostURL.lastIndexOf(':');
            if (idx == -1) {
                host = hostURL;
            } else {
                host = hostURL.substring(0, idx);
                port = Integer.parseInt(hostURL.substring(idx + 1));
            }
        } else {
            InetSocketAddress address = (InetSocketAddress) nettyContext.channel().remoteAddress();
            host = address.getHostName();
            port = address.getPort();
        }

        if (tls) {
            return String.format("https://%s%s", port == 443 ? host : host + ":" + port, contextPath);
        } else {
            return String.format("http://%s%s", port == 80 ? host : host + ":" + port, contextPath);
        }
    }

    public String getOriginalHostAddress() {
        var forwardedFor = nettyRequest.headers().get("x-forwarded-for");
        if (forwardedFor != null) {
            return forwardedFor;
        } else {
            var address = (InetSocketAddress) nettyContext.channel().remoteAddress();
            return address.getAddress().getHostAddress();
        }
    }

    public String getOriginalHostName() {
        var ipAddress = getOriginalHostAddress();
        try {
            var inetAddress = InetAddress.getByName(ipAddress);
            return inetAddress.getHostName();
        } catch (UnknownHostException e) {
            return ipAddress;
        }
    }

    public ChannelHandlerContext getNettyChannelHandlerContext() {
        return nettyContext;
    }

    public HttpRequest getNettyHttpRequest() {
        return nettyRequest;
    }

    /**
     * @throws ClassCastException
     *             if this context does not contain a "full" HTTP request.
     */
    public FullHttpRequest getNettyFullHttpRequest() {
        return (FullHttpRequest) nettyRequest;
    }

    public String getContextPath() {
        return contextPath;
    }

    public String getPathWithoutContext() {
        return HttpUtils.getPathWithoutContext(nettyRequest, contextPath);
    }

    public boolean isGET() {
        return nettyRequest.method() == HttpMethod.GET;
    }

    public boolean isPOST() {
        return nettyRequest.method() == HttpMethod.POST;
    }

    public void requireGET() {
        requireMethod(HttpMethod.GET);
    }

    public void requirePOST() {
        requireMethod(HttpMethod.POST);
    }

    public void requireMethod(HttpMethod... allowedMethods) {
        for (HttpMethod allowedMethod : allowedMethods) {
            if (nettyRequest.method() == allowedMethod) {
                return;
            }
        }
        throw new MethodNotAllowedException(nettyRequest.method(), nettyRequest.uri(),
                Arrays.asList(allowedMethods));
    }

    public void requireFormEncoding() {
        if (!isFormEncoded()) {
            throw new BadRequestException("Request is not form-encoded");
        }
    }

    public String requireFormParameter(String parameter) {
        String value = getFormParameter(parameter);
        if (value == null) {
            throw new BadRequestException("Missing form parameter '" + parameter + "'");
        }
        return value;
    }

    public String requireQueryParameter(String parameter) {
        String value = getQueryParameter(parameter);
        if (value == null) {
            throw new BadRequestException("Missing query parameter '" + parameter + "'");
        }
        return value;
    }

    public String requireParameter(String parameter) {
        String value = getParameter(parameter);
        if (value == null) {
            throw new BadRequestException("Missing parameter '" + parameter + "'");
        }
        return value;
    }

    public String getHeader(CharSequence name) {
        return nettyRequest.headers().get(name);
    }

    public boolean isFormEncoded() {
        return "application/x-www-form-urlencoded".equals(nettyRequest.headers().get(CONTENT_TYPE));
    }

    public String getCredentials(String type) {
        String authorizationHeader = getHeader(AUTHORIZATION);
        if (authorizationHeader != null) {
            String prefix = type + " ";
            if (authorizationHeader.startsWith(prefix)) {
                return authorizationHeader.substring(prefix.length());
            }
        }
        return null;
    }

    public String[] getBasicCredentials() {
        String userpassEncoded = getCredentials("Basic");
        if (userpassEncoded != null) {
            String userpassDecoded;
            try {
                userpassDecoded = new String(Base64.getDecoder().decode(userpassEncoded));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Could not decode Base64-encoded credentials");
            }
            String[] parts = userpassDecoded.split(":", 2);
            if (parts.length < 2) {
                throw new BadRequestException("Malformed username/password (Not separated by colon?)");
            }
            try {
                return new String[] { URLDecoder.decode(parts[0], "UTF-8"), URLDecoder.decode(parts[1], "UTF-8") };
            } catch (UnsupportedEncodingException e) {
                throw new InternalServerErrorException(e);
            }
        }
        return null;
    }

    public String getFormParameter(String parameter) {
        if (!isFormEncoded()) {
            return null;
        }

        if (formParameters == null) {
            formParameters = new HashMap<>();
            HttpPostRequestDecoder formDecoder = new HttpPostRequestDecoder(nettyRequest);
            try {
                for (InterfaceHttpData d : formDecoder.getBodyHttpDatas()) {
                    if (d.getHttpDataType() == HttpDataType.Attribute) {
                        formParameters.put(d.getName(), ((Attribute) d).getValue());
                    }
                }
            } catch (IOException e) {
                throw new InternalServerErrorException(e);
            } finally {
                formDecoder.destroy();
            }
        }
        return formParameters.get(parameter);
    }

    public String getQueryParameter(String parameter) {
        List<String> matches = qsDecoder.parameters().get(parameter);
        if (matches == null || matches.isEmpty()) {
            return null;
        } else {
            return matches.get(0);
        }
    }

    public String getParameter(String parameter) {
        String match = getQueryParameter(parameter);
        if (match == null && isFormEncoded()) {
            return getFormParameter(parameter);
        }
        return match;
    }

    public ByteBuf createByteBuf() {
        return nettyContext.alloc().buffer();
    }

    public void renderOK(String templateResource, Map<String, Object> vars) {
        render(HttpResponseStatus.OK, templateResource, vars);
    }

    public void render(HttpResponseStatus status, String templateResource, Map<String, Object> vars) {
        String processed = renderToString(templateResource, vars);

        ByteBuf body = nettyContext.alloc().buffer();
        body.writeCharSequence(processed, StandardCharsets.UTF_8);
        HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, body);
        response.headers().set(CONTENT_TYPE, TEXT_HTML);
        response.headers().set(CONTENT_LENGTH, body.readableBytes());
        sendResponse(response);
    }

    public String renderToString(String templateResource, Map<String, Object> vars) {
        try (InputStream resource = HandlerContext.class.getResourceAsStream(templateResource);
                InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            String template = CharStreams.toString(reader);
            return TemplateProcessor.process(template, vars);
        } catch (IOException | ParseException e) {
            throw new InternalServerErrorException(e);
        }
    }

    public void sendOK() {
        HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(CONTENT_LENGTH, 0);
        sendResponse(response);
    }

    public void sendOK(Message message) {
        HttpRequestHandler.sendMessageResponse(nettyContext, nettyRequest, HttpResponseStatus.OK, message);
    }

    public void sendOK(JsonObject jsonObject) {
        ByteBuf body = nettyContext.alloc().buffer();
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(jsonObject);
        body.writeCharSequence(json, StandardCharsets.UTF_8);
        HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK, body);
        response.headers().set(CONTENT_TYPE, "application/json");
        response.headers().set(CONTENT_LENGTH, body.readableBytes());
        HttpRequestHandler.sendResponse(nettyContext, nettyRequest, response);
    }

    public void sendAllow(HttpMethod... methods) {
        var response = new DefaultFullHttpResponse(HTTP_1_1, OK);
        response.headers().set(ALLOW, Arrays.asList(methods));
        response.headers().set(CONTENT_LENGTH, 0);
        sendResponse(response);
    }

    /**
     * Send a classpath resource.
     */
    public void sendResource(String resource) {
        var body = createByteBuf();
        try (var in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                throw new NotFoundException();
            }
            try (var out = new ByteBufOutputStream(body)) {
                ByteStreams.copy(in, out);
            }
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        var response = new DefaultFullHttpResponse(HTTP_1_1, OK, body);
        response.headers().set(CONTENT_TYPE, MIME.getMimetype(resource));
        response.headers().set(CONTENT_LENGTH, body.readableBytes());
        response.headers().set(CACHE_CONTROL, "private, max-age=86400");
        HttpRequestHandler.sendResponse(nettyContext, nettyRequest, response);
    }

    public ChannelFuture sendResponse(HttpResponse response) {
        return HttpRequestHandler.sendResponse(nettyContext, nettyRequest, response);
    }

    public void sendRedirect(String location) {
        HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().set(CONTENT_LENGTH, 0);
        response.headers().set(LOCATION, location);
        HttpRequestHandler.sendResponse(nettyContext, nettyRequest, response);
    }
}
