package org.yamcs.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.api.HttpBody;
import org.yamcs.logging.Log;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.QueryStringDecoder;

/**
 * Converts HTTP requests to Protobuf messages used in API definitions.
 * <p>
 * This is largely inspired from how Google Cloud transcodes HTTP to gRPC. The advantage of transcoding is that the API
 * implementation can be largely agnostic of HTTP and that it can profit from Protobuf generated code without needing to
 * distinguish between route params, query params, request bodies and so on.
 */
public class HttpTranscoder {

    private static final Log log = new Log(HttpTranscoder.class);

    public static Message transcode(RouteContext ctx) throws HttpTranscodeException {
        QueryStringDecoder qsDecoder = new QueryStringDecoder(ctx.getURI());
        Message requestPrototype = ctx.getRequestPrototype();
        Message.Builder requestb = requestPrototype.newBuilderForType();

        String body = ctx.getBodySpecifier();
        if (body != null && !ctx.isClientStreaming()) {
            if ("*".equals(body)) {
                requestb = ctx.getBodyAsMessage(requestb);
            } else {
                FieldDescriptor field = requestPrototype.getDescriptorForType().findFieldByName(body);
                if (field.getMessageType().equals(HttpBody.getDescriptor())) {
                    HttpBody httpBody = toHttpBody(ctx);
                    requestb.setField(field, httpBody);
                } else {
                    Message.Builder fieldValueb = requestb.getFieldBuilder(field);
                    Message fieldValue = ctx.getBodyAsMessage(fieldValueb).build();
                    requestb.setField(field, fieldValue);
                }
            }
        } else if (!ctx.isClientStreaming() && ctx.hasBody()) {
            log.warn("Received a request with a body, but the method {} does not support request bodies",
                    ctx.getMethod().getFullName());
        }

        for (FieldDescriptor field : requestPrototype.getDescriptorForType().getFields()) {
            if (ctx.hasRouteParam(field.getJsonName())) {
                Object value = toFieldValue(field, ctx.getRouteParam(field.getJsonName()));
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

    private static HttpBody toHttpBody(RouteContext ctx) {
        String contentType = ctx.nettyRequest.headers().get(HttpHeaderNames.CONTENT_ENCODING);

        HttpBody.Builder bodyb = HttpBody.newBuilder();
        if (contentType != null) {
            bodyb.setContentType(contentType);
        }
        if (ctx.hasBody()) {
            try (InputStream bufOut = ctx.getBodyAsInputStream()) {
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
                try {
                    return Timestamps.parse(parameter);
                } catch (ParseException e) {
                    throw new HttpTranscodeException("Provided date string does not conform to RFC 3339", e);
                }
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
