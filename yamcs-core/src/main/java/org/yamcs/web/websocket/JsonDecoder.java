package org.yamcs.web.websocket;

import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

import com.google.protobuf.MessageLite;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.yamcs.api.ws.WSConstants;

public class JsonDecoder implements WebSocketDecoder {

    private JsonFactory jsonFactory = new JsonFactory();

    /**
     * Decodes the first few common wrapper fields of an incoming web socket message.<br>
     * Sample: [1,1,2,{"&lt;resource&gt;":"&lt;operation&gt;", "data": &lt;undecoded remainder&gt;}]
     */
    @Override
    public WebSocketDecodeContext decodeMessage(InputStream in) throws WebSocketException {
	int requestId = WSConstants.NO_REQUEST_ID;
	try {
	    JsonParser jsp = jsonFactory.createParser(in);
	    requireTerminalToken(requestId, jsp, JsonToken.START_ARRAY);

	    // PROTOCOL VERSION
	    requireToken(requestId, jsp, JsonToken.VALUE_NUMBER_INT, "version");
	    int version = jsp.getIntValue();
	    if (version != WSConstants.PROTOCOL_VERSION)
		throw new WebSocketException(requestId, "Invalid version (expected " + WSConstants.PROTOCOL_VERSION + ", but got " + version);

	    // MESSAGE TYPE. Currently fixed for client-to-server messages
	    requireToken(requestId, jsp, JsonToken.VALUE_NUMBER_INT, "message type");
	    int messageType = jsp.getIntValue();
	    if (messageType != WSConstants.MESSAGE_TYPE_REQUEST) {
		throw new WebSocketException(requestId, "Invalid message type (expected " + WSConstants.MESSAGE_TYPE_REQUEST + ", but got " + messageType);
	    }

	    // SEQUENCE NUMBER. Check whether positive, because we use -1 for indicating that there is no known valid request-id
	    requireToken(requestId, jsp, JsonToken.VALUE_NUMBER_INT, "requestId");
	    int candidateRequestId = jsp.getIntValue();
	    if (candidateRequestId < 0) {
		throw new WebSocketException(requestId, "Invalid requestId. This needs to be a positive number");
	    }
	    requestId = candidateRequestId;

	    // TODO not sure why this is an object, maybe because it contains options?
	    requireTerminalToken(requestId, jsp, JsonToken.START_OBJECT);

	    // RESOURCE
	    requireToken(requestId, jsp, JsonToken.FIELD_NAME, "resource");
	    String resource = jsp.getCurrentName();

	    // OPERATION ON THAT RESOURCE
	    requireToken(requestId, jsp, JsonToken.VALUE_STRING, "operation");
	    String operation = jsp.getText();

	    WebSocketDecodeContext ctx = new WebSocketDecodeContext(version, messageType, requestId, resource, operation);

	    // Position JsonParser so that it point directly to the actual message (if any)
	    if (jsp.nextToken() != null) {
		if ((jsp.getCurrentToken()==JsonToken.FIELD_NAME) && (!"data".equals(jsp.getCurrentName()))) {
		    throw new WebSocketException(requestId, "Invalid message (expecting data as the next field)");
		}
	    }

	    // JsonParser is greedy, so hide our parser back in the returned message for later reuse
	    // alternative is releaseBuffered, but even with that it chops off the START_OBJECT
	    ctx.setData(jsp);

	    return ctx;
	} catch (IOException e) {
	    throw new WebSocketException(requestId, e);
	}
    }

    protected static void requireToken(int requestId, JsonParser jsp, JsonToken token, String type) throws IOException, WebSocketException {
	if (jsp.nextToken() != token) {
	    JsonLocation loc = jsp.getCurrentLocation();
	    throw new WebSocketException(requestId, String.format(
		    "Invalid message at line %d column %d: Expected '%s' token for %s but got '%s' instead)",
		    loc.getLineNr(), loc.getColumnNr(), token.asString(), type, jsp.getCurrentToken()));
	}
    }

    protected static void requireTerminalToken(int requestId, JsonParser jsp, JsonToken token) throws IOException, WebSocketException {
	if (jsp.nextToken() != token) {
	    JsonLocation loc = jsp.getCurrentLocation();
	    String expected = (token.asString() == null) ? token.toString() : token.asString();
	    String actual = (jsp.getCurrentToken().asString() == null) ? jsp.getCurrentToken().toString() : jsp.getCurrentToken().asString();
	    throw new WebSocketException(requestId, String.format(
		    "Invalid message at line %d column %d: Expected '%s' token but got '%s' instead)",
		    loc.getLineNr(), loc.getColumnNr(), expected, actual));
	}
    }

    @Override
    public <T extends MessageLite.Builder> T decodeMessageData(WebSocketDecodeContext ctx, Schema<T> schema) throws WebSocketException {
	// Re-use our earlier JsonParser, since that is correctly positioned
	try {
	    T msg = schema.newMessage();
	    JsonIOUtil.mergeFrom((JsonParser) ctx.getData(), msg, schema, false);
	    return msg;
	} catch (IOException e) {
	    throw new WebSocketException(ctx.getRequestId(), e);
	}
    }

    public static void main(String... args) throws WebSocketException {
	WebSocketDecodeContext ctx =
		new JsonDecoder().decodeMessage(new ByteArrayInputStream("[1,1,3,{\"cmdhistory\":\"subscribe\"}]".getBytes()));

	System.out.println("ctx "+ctx.getRequestId());
    }
}
