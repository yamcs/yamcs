package org.yamcs.http.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.yamcs.http.auth.AuthHandler.isSameOrigin;
import static org.yamcs.http.auth.AuthHandler.isValidRedirectUri;

import org.junit.jupiter.api.Test;

public class AuthHandlerTest {

    @Test
    public void testSameOrigin() {
        assertTrue(isSameOrigin("https://yamcs.example.com/cb", "https://yamcs.example.com"));
        assertTrue(isSameOrigin("https://yamcs.example.com:443/yamcs/cb?x=1", "https://yamcs.example.com"));
        assertTrue(isSameOrigin("http://yamcs.example.com/cb", "http://yamcs.example.com:80"));
        assertTrue(isSameOrigin("HTTPS://YAMCS.example.com/cb", "https://yamcs.EXAMPLE.com"));
        assertTrue(isSameOrigin("http://localhost:8090/cb", "http://localhost:8090"));
    }

    @Test
    public void testDifferentOrigin() {
        assertFalse(isSameOrigin("https://evil.example/cb", "https://yamcs.example.com"));
        assertFalse(isSameOrigin("http://yamcs.example.com/cb", "https://yamcs.example.com"));
        assertFalse(isSameOrigin("http://localhost:8091/cb", "http://localhost:8090"));
        assertFalse(isSameOrigin("https://yamcs.example.com@evil.example/cb", "https://yamcs.example.com"));
    }

    @Test
    public void testInvalid() {
        assertFalse(isSameOrigin("/cb", "https://yamcs.example.com"));
        assertFalse(isSameOrigin("//yamcs.example.com/cb", "https://yamcs.example.com"));
        assertFalse(isSameOrigin("javascript:alert(1)", "https://yamcs.example.com"));
        assertFalse(isSameOrigin("https://yamcs.example.com/cb", "null"));
        assertFalse(isSameOrigin("https://yamcs.example.com/cb", null));
        assertFalse(isSameOrigin(null, "https://yamcs.example.com"));
        assertFalse(isSameOrigin("http://[bad", "https://yamcs.example.com"));

        assertTrue(isValidRedirectUri("https://yamcs.example.com/cb"));
        assertFalse(isValidRedirectUri("/cb"));
        assertFalse(isValidRedirectUri("//evil.example/cb"));
        assertFalse(isValidRedirectUri("javascript:alert(1)"));
    }
}
