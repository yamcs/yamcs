package org.yamcs.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.yamcs.client.base.ServerURL;

public class ServerURLTest {

    @Test
    public void testDefaultHttp() {
        ServerURL url = ServerURL.parse("http://bla");
        assertEquals("bla", url.getHost());
        assertEquals(80, url.getPort()); // Respect HTTP default (no 8090)
        assertEquals(false, url.isTLS());
        assertEquals(null, url.getContext());
        assertEquals("http://bla", url.toString());
    }

    @Test
    public void testDefaultHttps() {
        ServerURL url = ServerURL.parse("https://bla");
        assertEquals("bla", url.getHost());
        assertEquals(443, url.getPort()); // Respect HTTPS default
        assertEquals(true, url.isTLS());
        assertEquals(null, url.getContext());
        assertEquals("https://bla", url.toString());
    }

    @Test
    public void testPortOverride() {
        ServerURL url = ServerURL.parse("http://bla:8090");
        assertEquals("bla", url.getHost());
        assertEquals(8090, url.getPort());
        assertEquals(false, url.isTLS());
        assertEquals(null, url.getContext());
        assertEquals("http://bla:8090", url.toString());
    }

    @Test
    public void testTrailingSlash() {
        ServerURL url = ServerURL.parse("http://bla:8090/");
        assertEquals("bla", url.getHost());
        assertEquals(8090, url.getPort());
        assertEquals(false, url.isTLS());
        assertEquals(null, url.getContext());
        assertEquals("http://bla:8090", url.toString());
    }

    @Test
    public void testContext() {
        ServerURL url = ServerURL.parse("http://bla:8090/foo");
        assertEquals("bla", url.getHost());
        assertEquals(8090, url.getPort());
        assertEquals(false, url.isTLS());
        assertEquals("foo", url.getContext());
        assertEquals("http://bla:8090/foo", url.toString());
    }

    @Test
    public void testContextWithTrailingSlash() {
        ServerURL url = ServerURL.parse("http://bla:8090/foo/");
        assertEquals("bla", url.getHost());
        assertEquals(8090, url.getPort());
        assertEquals(false, url.isTLS());
        assertEquals("foo", url.getContext());
        assertEquals("http://bla:8090/foo", url.toString());
    }

    @Test
    public void testDeepContext() {
        ServerURL url = ServerURL.parse("http://bla:8090/foo/bar");
        assertEquals("bla", url.getHost());
        assertEquals(8090, url.getPort());
        assertEquals(false, url.isTLS());
        assertEquals("foo/bar", url.getContext());
        assertEquals("http://bla:8090/foo/bar", url.toString());
    }

    @Test
    public void testDeepContextWithTrailingSlash() {
        ServerURL url = ServerURL.parse("http://bla:8090/foo/bar/");
        assertEquals("bla", url.getHost());
        assertEquals(8090, url.getPort());
        assertEquals(false, url.isTLS());
        assertEquals("foo/bar", url.getContext());
        assertEquals("http://bla:8090/foo/bar", url.toString());
    }

    @Test
    public void testInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            ServerURL.parse("bla");
        });
    }
}
