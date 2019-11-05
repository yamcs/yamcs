package org.yamcs.http.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.api.Api;
import org.yamcs.api.HttpBody;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.QueryStringDecoder;

/**
 * Converts HTTP requests to Protobuf messages used in API definitions.
 * <p>
 * This is largely inspired on how Google Cloud transcodes HTTP to gRPC. The advantage of transcoding is that our API
 * implementation can be largely agnostic of HTTP and that it can profit from Protobuf generated code without needing to
 * distinguish between route params, query params, request bodies and so on.
 * <p>
 * In other words: transcoding allow us to design contract-first APIs based on the proto definition.
 */
public class HttpTranscoder {

    public static Message transcode(RestRequest restRequest, Api<Context> api, MethodDescriptor method,
            RouteConfig routeConfig) throws HttpTranscodeException {
        QueryStringDecoder qsDecoder = new QueryStringDecoder(restRequest.getHttpRequest().uri());
        Message requestPrototype = api.getRequestPrototype(method);
        Message.Builder requestb = requestPrototype.newBuilderForType();

        String body = routeConfig.getBody();
        if (body != null) {
            if ("*".equals(body)) {
                requestb = restRequest.bodyAsMessage(requestb);
            } else {
                FieldDescriptor field = requestPrototype.getDescriptorForType().findFieldByName(body);
                if (field.getMessageType().equals(HttpBody.getDescriptor())) {
                    HttpBody httpBody = toHttpBody(restRequest);
                    requestb.setField(field, httpBody);
                } else {
                    Message.Builder fieldValueb = requestb.getFieldBuilder(field);
                    Message fieldValue = restRequest.bodyAsMessage(fieldValueb).build();
                    requestb.setField(field, fieldValue);
                }
            }
        }

        for (FieldDescriptor field : requestPrototype.getDescriptorForType().getFields()) {
            if (restRequest.hasRouteParam(field.getJsonName())) {
                Object value = toFieldValue(field, restRequest.getRouteParam(field.getJsonName()));
                requestb.setField(field, value);
            } else if (body == null) {
                List<String> queryParameter = qsDecoder.parameters().get(field.getJsonName());
                if (queryParameter != null) {
                    Object value = toFieldValue(field, queryParameter);
                    requestb.setField(field, value);
                }
            }
        }

        return requestb.build();
    }

    private static HttpBody toHttpBody(RestRequest restRequest) {
        String contentType = restRequest.getHttpRequest().headers().get(HttpHeaderNames.CONTENT_ENCODING);

        HttpBody.Builder bodyb = HttpBody.newBuilder();
        if (contentType != null) {
            bodyb.setContentType(contentType);
        }
        if (restRequest.hasBody()) {
            try (InputStream bufOut = restRequest.bodyAsInputStream()) {
                ByteString data = ByteString.readFrom(bufOut);
                bodyb.setData(data);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return bodyb.build();
    }

    private static Object toFieldValue(FieldDescriptor field, List<String> parameters) throws HttpTranscodeException {
        if (field.isRepeated()) {
            List<Object> values = new ArrayList<>();
            for (String value : parameters) {
                for (String item : value.split(",")) { // Support both repeated query params and comma-separated
                    values.add(toFieldValue(field, item));
                }
            }
            return values;
        } else {
            return toFieldValue(field, parameters.get(0));
        }
    }

    private static Object toFieldValue(FieldDescriptor field, String parameter) throws HttpTranscodeException {
        String name = field.getJsonName();
        switch (field.getJavaType()) {
        case BOOLEAN:
            return toBoolean(name, parameter);
        case INT:
            return toInt(name, parameter);
        case LONG:
            return toLong(name, parameter);
        case STRING:
            return parameter;
        case ENUM:
            return field.getEnumType().findValueByName(parameter);
        case MESSAGE:
            if (Timestamp.getDescriptor().equals(field.getMessageType())) {
                long instant = TimeEncoding.parse(parameter);
                return TimeEncoding.toProtobufTimestamp(instant);
            }
            throw new UnsupportedOperationException(
                    "No query parameter conversion for message type " + field.getMessageType().getFullName());
        default:
            throw new UnsupportedOperationException(
                    "No query parameter conversion for type " + field.getJavaType());
        }
    }

    private static boolean toBoolean(String name, String parameter) {
        return (parameter == null || "".equals(parameter) || "true".equalsIgnoreCase(parameter)
                || "yes".equalsIgnoreCase(parameter));
    }

    private static int toInt(String name, String parameter) throws HttpTranscodeException {
        try {
            return Integer.parseInt(parameter);
        } catch (NumberFormatException e) {
            throw new HttpTranscodeException(String.format(
                    "Parameter '%s' is not a valid integer value", name));
        }
    }

    private static long toLong(String name, String parameter) throws HttpTranscodeException {
        try {
            return Long.parseLong(parameter);
        } catch (NumberFormatException e) {
            throw new HttpTranscodeException(String.format(
                    "Parameter '%s' is not a valid integer value", name));
        }
    }
}
