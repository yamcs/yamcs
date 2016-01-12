package org.yamcs.web.rest;

import static io.netty.handler.codec.http.HttpMethod.DELETE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.PATCH;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.List;
import java.util.regex.MatchResult;

import org.junit.Before;
import org.junit.Test;
import org.yamcs.web.MethodNotAllowedException;
import org.yamcs.web.RouteHandler;
import org.yamcs.web.rest.Router.RouteMatch;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpMethod;

public class RouterTest {
    
    private Router router;
    
    @Before
    public void before() {
        router = new Router();
        
        router.registerRouteHandler(null, new RouteHandler() {
            
            // Some simple routes
            @Route(path = "/a/:adjective/path") public void pathA() {}
            @Route(path = "/b/:adjective?/path") public void pathB() {}
            @Route(path = "/c/:adjective*/path") public void pathC() {}
            
            // Identical paths, but different HTTP methods
            @Route(path = "/d/:adjective/path", method = { "POST", "PATCH" }) public void pathM() {}
            @Route(path = "/d/:adjective/path", method = "GET") public void pathN() {}
            
            // Multiple paths, same java method
            @Route(path = "/e/:adjective/path")
            @Route(path = "/f/different/path")
            public void pathS() {}
            
            // A more complicated set of rules sharing partial paths.
            // Route order is in principle non-deterministic, due to java reflection limitations.
            // Our algorithm is designed to stop on the first match by descending
            // string length. In addition, we have a flag to indicate priority, but
            // try to architect paths differently
            @Route(path = "/g/archive/:instance") public void pathU() {}
            @Route(path = "/g/archive/:instance/parameters") public void pathV() {}
            @Route(path = "/g/archive/:instance/parameters/bulk", priority = true) public void pathW() {}
            @Route(path = "/g/archive/:instance/parameters/:name*") public void pathY() {}
            @Route(path = "/g/archive/:instance/parameters/:name*/series") public void pathX() {}
        });
    }
    
    @Test
    public void testUnmatchedURI() throws MethodNotAllowedException {
        assertNull(router.matchURI(GET, "garbage"));
    }
    
    @Test
    public void testUnmatchedMethod() throws MethodNotAllowedException {
        assertNotNull(router.matchURI(GET, "/a/great/path"));
        
        boolean failed = false;
        try {
            assertNull(router.matchURI(POST, "/a/great/path"));
        } catch (MethodNotAllowedException e) {
            failed = true;
            List<HttpMethod> allowedMethods = e.getAllowedMethods();
            assertEquals(1, allowedMethods.size());
            assertEquals(GET, allowedMethods.get(0));
        }
        assertTrue(failed);
    }
    
    @Test
    public void testUnmatchedMethod_multipleRoutes() throws MethodNotAllowedException {
        assertNotNull(router.matchURI(GET, "/d/great/path"));
        
        boolean failed = false;
        try {
            assertNull(router.matchURI(DELETE, "/d/great/path"));
        } catch (MethodNotAllowedException e) {
            failed = true;
            List<HttpMethod> allowedMethods = e.getAllowedMethods();
            assertEquals(3, allowedMethods.size());
            // Alphabetic, and combining methods from all routes
            assertEquals(GET, allowedMethods.get(0));
            assertEquals(PATCH, allowedMethods.get(1));
            assertEquals(POST, allowedMethods.get(2));
        }
        assertTrue(failed);
    }
    
    @Test
    public void testSimpleMatch() throws MethodNotAllowedException {
        MatchResult res = router.matchURI(GET, "/a/great/path").regexMatch;
        assertEquals("great", res.group(1));
    }
    
    @Test
    public void testSimpleOptionalMatch() throws MethodNotAllowedException {
        MatchResult res = router.matchURI(GET, "/b/fascinating/path").regexMatch;
        assertEquals("fascinating", res.group(1));
        
        res = router.matchURI(GET, "/b/path").regexMatch;
        assertEquals(null, res.group(1));
    }
    
    @Test
    public void testSimpleStarMatch() throws MethodNotAllowedException {
        RouteMatch match = router.matchURI(GET, "/c/really/great/fascinating/path");
        assertEquals("really/great/fascinating", match.regexMatch.group(1));
        
        match = router.matchURI(GET, "/c/path");
        assertNull("Star must match at least one segment", match);
    }
    
    @Test
    public void testDistinctMethodMatch() throws MethodNotAllowedException {
        RouteMatch match1 = router.matchURI(GET, "/d/great/path");
        MethodHandleInfo info1 = MethodHandles.lookup().revealDirect(match1.routeConfig.handle);
        assertEquals("pathN", info1.getName());

        RouteMatch match2 = router.matchURI(POST, "/d/great/path");
        MethodHandleInfo info2 = MethodHandles.lookup().revealDirect(match2.routeConfig.handle);
        assertEquals("pathM", info2.getName());
    }
    
    @Test
    public void testMultipleRouteMatch() throws MethodNotAllowedException {
        RouteMatch match1 = router.matchURI(GET, "/e/great/path");
        RouteMatch match2 = router.matchURI(GET, "/f/different/path");
        
        Lookup lookup = MethodHandles.lookup();
        MethodHandleInfo info1 = lookup.revealDirect(match1.routeConfig.handle);
        MethodHandleInfo info2 = lookup.revealDirect(match2.routeConfig.handle);
        assertEquals(info1.getName(), info2.getName());
    }
    
    @Test
    public void testMultipleRouteMatching() throws MethodNotAllowedException {
        MatchResult res = router.matchURI(GET, "/g/archive/simulator").regexMatch;
        assertEquals(1, res.groupCount());
        assertEquals("simulator", res.group(1));
        
        res = router.matchURI(GET, "/g/archive/simulator/parameters/YSS/SIMULATOR/BatteryVoltage1").regexMatch;
        assertEquals(2, res.groupCount());
        assertEquals("simulator", res.group(1));
        assertEquals("YSS/SIMULATOR/BatteryVoltage1", res.group(2));
        
        res = router.matchURI(GET, "/g/archive/simulator/parameters/bulk").regexMatch;
        assertEquals(1, res.groupCount());
        assertEquals("simulator", res.group(1));
        
        res = router.matchURI(GET, "/g/archive/simulator/parameters/YSS/SIMULATOR/BatteryVoltage1/series").regexMatch;
        assertEquals(2, res.groupCount());
        assertEquals("simulator", res.group(1));
        assertEquals("YSS/SIMULATOR/BatteryVoltage1", res.group(2));
    }
    
    @Test
    public void testRouteParams() throws MethodNotAllowedException {
        MockRestRouter router = new MockRestRouter();
        router.registerRouteHandler(null, new RouteHandler() {
            @Route(path = "/h/archive/:bla?/:instance")
            public ChannelFuture abc(RestRequest req) {
                return null;
            }
        });
        
        RouteMatch match = router.matchURI(GET, "/h/archive/simulator");
        MockRestRequest mockRequest = new MockRestRequest();
        mockRequest.setRouteMatch(match);
        router.dispatch(mockRequest, match);
        
        Lookup lookup = MethodHandles.lookup();
        MethodHandleInfo info = lookup.revealDirect(match.routeConfig.handle);
        assertEquals("abc", info.getName());
        
        RestRequest observedRestRequest = router.observedRestRequest;
        assertTrue(observedRestRequest.hasRouteParam("instance"));
        assertEquals("simulator", observedRestRequest.getRouteParam("instance"));
    }
    
    private static final class MockRestRouter extends Router {
        
        RestRequest observedRestRequest;
        
        @Override
        protected void dispatch(RestRequest req, RouteMatch match) {
            observedRestRequest = req;
        }
    }
    
    private static final class MockRestRequest extends RestRequest {
        public MockRestRequest() {
            super(null, null, null, null);
        }
    }
}
