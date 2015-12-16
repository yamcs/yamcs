package org.yamcs.web.rest;

import static junit.framework.Assert.assertEquals;

import org.junit.Test;
import org.yamcs.api.MediaType;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;

public class RestRequestTest {

    @Test
    public void testMediaType_unspecified() {
        RestRequest req = makeRestRequest(null, null);

        MediaType in = req.deriveSourceContentType();
        assertEquals(MediaType.JSON, in);

        MediaType out = req.deriveTargetContentType();
        assertEquals(in, out); // Match with default in, if unspecified
    }

    @Test
    public void testMediaType_wildcard() {
        RestRequest req = makeRestRequest(null, MediaType.from("*/*"));  // curl uses this by default

        MediaType in = req.deriveSourceContentType();
        assertEquals(MediaType.JSON, in);

        MediaType out = req.deriveTargetContentType();
        assertEquals(in, out);
    }

    @Test
    public void testMediaType_ContentType_only() {
        RestRequest req = makeRestRequest(MediaType.JSON, null);

        MediaType in = req.deriveSourceContentType();
        assertEquals(MediaType.JSON, in);

        MediaType out = req.deriveTargetContentType();
        assertEquals(in, out); // Match with in, if unspecified
    }

    @Test
    public void testMediaType_unsupported_ContentType() {
        // We currently don't throw an error for this
        RestRequest req = makeRestRequest(MediaType.from("blabla"), null);

        MediaType in = req.deriveSourceContentType();
        assertEquals(MediaType.JSON, in);

        MediaType out = req.deriveTargetContentType();
        assertEquals(MediaType.JSON, out); // Match with default in, if unspecified
    }

    @Test
    public void testMediaType_cross_match() {
        RestRequest req = makeRestRequest(MediaType.PROTOBUF, MediaType.JSON);

        MediaType in = req.deriveSourceContentType();
        assertEquals(MediaType.PROTOBUF, in);

        MediaType out = req.deriveTargetContentType();
        assertEquals(MediaType.JSON, out);
    }

    private static RestRequest makeRestRequest(MediaType contentType, MediaType accept) {
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        if (contentType != null) {
            req.headers().set(Names.CONTENT_TYPE, contentType);
        }
        if (accept != null) {
            req.headers().set(Names.ACCEPT, accept);
        }
        return new RestRequest(null, req, new QueryStringDecoder("/"), null);
    }
}
