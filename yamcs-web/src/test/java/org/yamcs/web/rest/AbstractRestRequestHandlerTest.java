package org.yamcs.web.rest;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class AbstractRestRequestHandlerTest {

    private AbstractRestRequestHandler mockHandler = new MockRestRequestHandler();

    @Test
    public void testMediaType_unspecified() {
        HttpRequest req = makeHttpRequest(null, null);

        String in = mockHandler.getSourceContentType(req);
        assertEquals(mockHandler.getSupportedInboundMediaTypes()[0], in);

        String out = mockHandler.getTargetContentType(req);
        assertEquals(in, out); // Match with default in, if unspecified
    }

    @Test
    public void testMediaType_wildcard() {
        HttpRequest req = makeHttpRequest(null, "*/*");  // curl uses this by default

        String in = mockHandler.getSourceContentType(req);
        assertEquals(mockHandler.getSupportedInboundMediaTypes()[0], in);

        String out = mockHandler.getTargetContentType(req);
        assertEquals(in, out);
    }

    @Test
    public void testMediaType_ContentType_only() {
        HttpRequest req = makeHttpRequest("application/json", null);

        String in = mockHandler.getSourceContentType(req);
        assertEquals("application/json", in);

        String out = mockHandler.getTargetContentType(req);
        assertEquals(in, out); // Match with in, if unspecified
    }

    @Test
    public void testMediaType_unsupported_ContentType() {
        // We currently don't throw an error for this
        HttpRequest req = makeHttpRequest("blabla", null);

        String in = mockHandler.getSourceContentType(req);
        assertEquals(mockHandler.getSupportedInboundMediaTypes()[0], in);

        String out = mockHandler.getTargetContentType(req);
        assertEquals("application/json", out); // Match with default in, if unspecified
    }

    @Test
    public void testMediaType_cross_match() {
        HttpRequest req = makeHttpRequest("application/octet-stream", "application/json");

        String in = mockHandler.getSourceContentType(req);
        assertEquals("application/octet-stream", in);

        String out = mockHandler.getTargetContentType(req);
        assertEquals("application/json", out);
    }

    private static HttpRequest makeHttpRequest(String contentType, String accept) {
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        if (contentType != null) {
            req.setHeader(Names.CONTENT_TYPE, contentType);
        }
        if (accept != null) {
            req.setHeader(Names.ACCEPT, accept);
        }
        return req;
    }

    private static final class MockRestRequestHandler extends AbstractRestRequestHandler {
        @Override
        public void handleRequest(ChannelHandlerContext ctx, HttpRequest httpRequest, MessageEvent evt, String yamcsInstance, String remainingUri) throws RestException {
            // NOP
        }
    }
}
