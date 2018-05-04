package org.yamcs.web.websocket;

import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.ws.WSConstants;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import io.netty.buffer.ByteBuf;

public class JsonDecoder implements WebSocketDecoder {
    private static final Logger log = LoggerFactory.getLogger(JsonDecoder.class);

    /**
     * Decodes the first few common wrapper fields of an incoming web socket message.<br>
     * Sample: [1,1,2,{"&lt;resource&gt;":"&lt;operation&gt;", "data": &lt;undecoded remainder&gt;}]
     */
    @Override
    public WebSocketDecodeContext decodeMessage(ByteBuf binary) throws WebSocketException {

        int requestId = WSConstants.NO_REQUEST_ID;
        String json = binary.toString(StandardCharsets.UTF_8);
        try {
            JsonElement el = new JsonParser().parse(json);
            if (!el.isJsonArray()) {
                throw new WebSocketException(requestId, "Not a JSON array: '" + json + "'");
            }
            JsonArray arr = el.getAsJsonArray();

            // PROTOCOL VERSION
            int version = arr.get(0).getAsInt();
            if (version != WSConstants.PROTOCOL_VERSION) {
                throw new WebSocketException(requestId, String.format(
                        "Invalid version (expected %s, but got %s", WSConstants.PROTOCOL_VERSION, version));
            }

            // MESSAGE TYPE. Currently fixed for client-to-server messages
            int messageType = arr.get(1).getAsInt();
            if (messageType != WSConstants.MESSAGE_TYPE_REQUEST) {
                throw new WebSocketException(requestId, "Invalid message type (expected "
                        + WSConstants.MESSAGE_TYPE_REQUEST + ", but got " + messageType);
            }

            // SEQUENCE NUMBER. Check whether positive, because we use -1 for indicating that there is no known valid
            // request-id
            int candidateRequestId = arr.get(2).getAsInt();
            if (candidateRequestId < 0) {
                throw new WebSocketException(requestId, "Invalid requestId. This needs to be a positive number");
            }
            requestId = candidateRequestId;

            JsonObject obj = arr.get(3).getAsJsonObject();
            String resource = null;
            String operation = null;
            JsonElement data = null;
            for (Entry<String, JsonElement> entry : obj.entrySet()) {
                if ("data".equals(entry.getKey())) {
                    data = entry.getValue();
                } else {
                    if (resource != null) {
                        throw new WebSocketException(requestId, "Only one subscription can be sent at a time");
                    }
                    resource = entry.getKey();
                    operation = entry.getValue().getAsString();
                }
            }
            if (resource == null || operation == null) {
                throw new WebSocketException(requestId, "Missing subscription");
            }

            WebSocketDecodeContext ctx = new WebSocketDecodeContext(version, messageType, requestId, resource,
                    operation);
            if (data != null) {
                ctx.setData(data);
            }

            return ctx;
        } catch (JsonParseException e) {
            log.warn("Failed to decode message as json: '{}': {}", json, e.getMessage());
            throw new WebSocketException(requestId, "Invalid message format");
        }
    }

    @Override
    public <T extends Message.Builder> T decodeMessageData(WebSocketDecodeContext ctx, T builder)
            throws WebSocketException {
        try {
            String json = ((JsonElement) ctx.getData()).toString();
            JsonFormat.parser().merge(json, builder);
            return builder;
        } catch (InvalidProtocolBufferException e) {
            throw new WebSocketException(ctx.getRequestId(), e);
        }
    }
}
