package org.yamcs.client.base;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.api.AnnotationsProto;
import org.yamcs.api.HttpBody;
import org.yamcs.api.HttpRoute;
import org.yamcs.api.MethodHandler;
import org.yamcs.api.Observer;
import org.yamcs.client.YamcsClient;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Duration;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringEncoder;

public class HttpMethodHandler implements MethodHandler {

    private static final Pattern PATTERN_TEMPLATE_VAR = Pattern.compile("\\{([^\\*\\?\\}]+)(\\*|\\?|\\*\\*)?\\}");

    private RestClient baseClient;
    private WebSocketClient webSocketClient;

    public HttpMethodHandler(YamcsClient client, RestClient baseClient, WebSocketClient webSocketClient) {
        this.baseClient = baseClient;
        this.webSocketClient = webSocketClient;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Observer<? extends Message> streamingCall(MethodDescriptor method, Message requestPrototype,
            Message responsePrototype, Observer<? extends Message> responseObserver) {
        return new ClientStreamingObserver(method, baseClient, responsePrototype, (Observer<Message>) responseObserver);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void call(MethodDescriptor method, Message request, Message responsePrototype,
            Observer<? extends Message> observer) {
        HttpRoute route = method.getOptions().getExtension(AnnotationsProto.route);

        // Holder for extracting route and query params
        Message.Builder partial = request.toBuilder();

        String template = getPattern(route);
        QueryStringEncoder uri = resolveUri(template, request, method.getInputType(), partial);
        Message body = null;
        if (route.hasBody()) {
            if ("*".equals(route.getBody())) {
                body = partial.buildPartial();
            } else {
                FieldDescriptor bodyField = method.getInputType().findFieldByName(route.getBody());
                if (!request.hasField(bodyField)) {
                    throw new IllegalArgumentException(
                            "Request message must have the field '" + route.getBody() + "' set");
                }
                body = (Message) request.getField(bodyField);
                partial.clearField(bodyField);
            }
        }

        HttpMethod httpMethod = getMethod(route);

        if (method.toProto().getServerStreaming()) {
            BulkRestDataReceiver receiver = data -> {
                try {
                    Message serverMessage = responsePrototype.toBuilder().mergeFrom(data).build();
                    ((Observer<Message>) observer).next(serverMessage);
                } catch (InvalidProtocolBufferException e) {
                    throw new IllegalArgumentException(e);
                }
            };
            CompletableFuture<Void> future;
            if (body == null) {
                appendQueryString(uri, partial.build(), method.getInputType());
                future = baseClient.doBulkRequest(httpMethod, uri.toString(), receiver);
            } else if (body instanceof HttpBody) {
                byte[] data = ((HttpBody) body).getData().toByteArray();
                future = baseClient.doBulkRequest(httpMethod, uri.toString(), data, receiver);
            } else {
                future = baseClient.doBulkRequest(httpMethod, uri.toString(), body.toByteArray(), receiver);
            }
            future.whenComplete((v, err) -> {
                if (err == null) {
                    observer.complete();
                } else {
                    observer.completeExceptionally(err);
                }
            });
        } else {
            CompletableFuture<byte[]> requestFuture;
            if (body == null) {
                appendQueryString(uri, partial.build(), method.getInputType());
                requestFuture = baseClient.doRequest(uri.toString(), httpMethod);
            } else if (body instanceof HttpBody) {
                byte[] data = ((HttpBody) body).getData().toByteArray();
                requestFuture = baseClient.doRequest(uri.toString(), httpMethod, data);
            } else {
                requestFuture = baseClient.doRequest(uri.toString(), httpMethod, body);
            }
            requestFuture.whenComplete((data, err) -> {
                if (err == null) {
                    if (responsePrototype instanceof HttpBody) {
                        Message serverMessage = HttpBody.newBuilder().setData(ByteString.copyFrom(data)).build();
                        ((Observer<Message>) observer).complete(serverMessage);
                    } else {
                        try {
                            Message serverMessage = responsePrototype.toBuilder().mergeFrom(data).build();
                            ((Observer<Message>) observer).complete(serverMessage);
                        } catch (Exception e) {
                            observer.completeExceptionally(e);
                        }
                    }
                } else {
                    observer.completeExceptionally(err);
                }
            });
        }
    }

    static QueryStringEncoder resolveUri(String template, Message input, Descriptor inputType,
            Message.Builder partial) {
        StringBuffer buf = new StringBuffer();
        Matcher matcher = PATTERN_TEMPLATE_VAR.matcher(template);
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            boolean optional = "**".equals(matcher.group(2)) || "?".equals(matcher.group(2));
            FieldDescriptor field = inputType.findFieldByName(fieldName);
            if (!optional && !input.hasField(field)) {
                throw new IllegalArgumentException(
                        "Request message is missing mandatory parameter '" + fieldName + "'");
            }

            Object fieldValue = input.getField(field);
            String stringValue = String.valueOf(fieldValue);

            String encodedValue = encodeURIComponent(stringValue);

            matcher.appendReplacement(buf, encodedValue);
            partial.clearField(field);
        }
        matcher.appendTail(buf);

        String uri = buf.toString().replace("/api", "");
        QueryStringEncoder encoder = new QueryStringEncoder(uri);
        return encoder;
    }

    private void appendQueryString(QueryStringEncoder encoder, Message queryHolder, Descriptor inputType) {
        for (Entry<FieldDescriptor, Object> entry : queryHolder.getAllFields().entrySet()) {
            FieldDescriptor descriptor = entry.getKey();
            if (descriptor.isRepeated()) {
                List<?> params = (List<?>) entry.getValue();
                for (Object param : params) {
                    encoder.addParam(descriptor.getJsonName(), formatQueryParam(param));
                }
            } else {
                encoder.addParam(descriptor.getJsonName(), formatQueryParam(entry.getValue()));
            }
        }
    }

    private static String formatQueryParam(Object value) {
        if (value instanceof Timestamp) {
            Timestamp proto = (Timestamp) value;
            Instant instant = Instant.ofEpochSecond(proto.getSeconds(), proto.getNanos());
            return instant.toString();
        } else if (value instanceof Duration) {
            Duration proto = (Duration) value;
            return String.format("%d.%09ds", proto.getSeconds(), proto.getNanos());
        } else {
            return String.valueOf(value);
        }
    }

    static HttpMethod getMethod(HttpRoute route) {
        switch (route.getPatternCase()) {
        case GET:
            return HttpMethod.GET;
        case POST:
            return HttpMethod.POST;
        case PATCH:
            return HttpMethod.PATCH;
        case PUT:
            return HttpMethod.PUT;
        case DELETE:
            return HttpMethod.DELETE;
        default:
            throw new IllegalStateException();
        }
    }

    static String getPattern(HttpRoute route) {
        switch (route.getPatternCase()) {
        case GET:
            return route.getGet();
        case POST:
            return route.getPost();
        case PATCH:
            return route.getPatch();
        case PUT:
            return route.getPut();
        case DELETE:
            return route.getDelete();
        default:
            throw new IllegalStateException();
        }
    }

    private static String encodeURIComponent(String component) {
        try {
            return URLEncoder.encode(component, "UTF-8")
                    .replace("\\+", "%20")
                    .replace("\\%21", "!")
                    .replace("\\%27", "'")
                    .replace("\\%28", "(")
                    .replace("\\%29", ")")
                    .replace("\\%7E", "~");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    // TODO It may be better to handle subscriptions in here, rather than in
    // AbstractSubscription
    public WebSocketClient getWebSocketClient() {
        return webSocketClient;
    }
}
