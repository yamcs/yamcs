package org.yamcs.web.rest;

import static junit.framework.Assert.assertEquals;
import static org.yamcs.web.AbstractRequestHandler.BINARY_MIME_TYPE;
import static org.yamcs.web.AbstractRequestHandler.JSON_MIME_TYPE;

import org.junit.Test;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

public class AbstractRestRequestHandlerTest {

    @Test
    public void testMediaType_unspecified() {
        RestRequest req = makeRestRequest(null, null);

        String in = req.deriveSourceContentType();
        assertEquals(JSON_MIME_TYPE, in);

        String out = req.deriveTargetContentType();
        assertEquals(in, out); // Match with default in, if unspecified
    }

    @Test
    public void testMediaType_wildcard() {
        RestRequest req = makeRestRequest(null, "*/*");  // curl uses this by default

        String in = req.deriveSourceContentType();
        assertEquals(JSON_MIME_TYPE, in);

        String out = req.deriveTargetContentType();
        assertEquals(in, out);
    }

    @Test
    public void testMediaType_ContentType_only() {
        RestRequest req = makeRestRequest(JSON_MIME_TYPE, null);

        String in = req.deriveSourceContentType();
        assertEquals(JSON_MIME_TYPE, in);

        String out = req.deriveTargetContentType();
        assertEquals(in, out); // Match with in, if unspecified
    }

    @Test
    public void testMediaType_unsupported_ContentType() {
        // We currently don't throw an error for this
        RestRequest req = makeRestRequest("blabla", null);

        String in = req.deriveSourceContentType();
        assertEquals(JSON_MIME_TYPE, in);

        String out = req.deriveTargetContentType();
        assertEquals(JSON_MIME_TYPE, out); // Match with default in, if unspecified
    }

    @Test
    public void testMediaType_cross_match() {
        RestRequest req = makeRestRequest(BINARY_MIME_TYPE, JSON_MIME_TYPE);

        String in = req.deriveSourceContentType();
        assertEquals(BINARY_MIME_TYPE, in);

        String out = req.deriveTargetContentType();
        assertEquals(JSON_MIME_TYPE, out);
    }

    private static RestRequest makeRestRequest(String contentType, String accept) {
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        if (contentType != null) {
            req.headers().set(Names.CONTENT_TYPE, contentType);
        }
        if (accept != null) {
            req.headers().set(Names.ACCEPT, accept);
        }
        return new RestRequest(null, req, null, "/a-path", null);
    }
}
