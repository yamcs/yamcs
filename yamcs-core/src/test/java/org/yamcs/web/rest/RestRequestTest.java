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
    public void testPathSegments() {
        RestRequest req = makeRestRequest("/api/simulator/parameters");
        assertEquals(4, req.getPathSegmentCount());
        assertEquals("", req.getPathSegment(0));
        assertEquals("api", req.getPathSegment(1));
    }
    
    @Test
    public void testSlicePath() {
        RestRequest req = makeRestRequest("/api/simulator/parameters");        
        assertEquals("api/simulator/parameters", req.slicePath(1));
        assertEquals("simulator/parameters", req.slicePath(2));
        assertEquals("parameters", req.slicePath(3));
        assertEquals("simulator/parameters", req.slicePath(2, 4));
        assertEquals("simulator", req.slicePath(2, 3));
        
        assertEquals("api/simulator", req.slicePath(1, -1));
        assertEquals("api", req.slicePath(1, -2));
        
        req = makeRestRequest("/api/simulator/parameters/MDB:OPS+Name/SIMULATOR_BatteryVoltage2");
        assertEquals("MDB:OPS Name", req.slicePath(4, -1));
        assertEquals("SIMULATOR_BatteryVoltage2", req.slicePath(5));
        
        req = makeRestRequest("/api/simulator/parameters/YSS/SIMULATOR/BatteryVoltage2");
        assertEquals("YSS/SIMULATOR", req.slicePath(4, -1));
        assertEquals("BatteryVoltage2", req.slicePath(-1));
    }

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
        return new RestRequest(null, req, new QueryStringDecoder("/"), null, null);
    }
    
    private static RestRequest makeRestRequest(String path) {
        return new RestRequest(null, null, new QueryStringDecoder(path), null, null);
    }
}
