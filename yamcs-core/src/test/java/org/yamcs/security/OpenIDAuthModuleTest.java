package org.yamcs.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class OpenIDAuthModuleTest {

    @Test
    public void testAuthorizationEncoding() {
        // Example from https://datatracker.ietf.org/doc/html/rfc6749#section-2.3.1
        var clientId = "s6BhdRkqt3";
        var clientSecret = "7Fjfp0ZBr1KtDRbnfVdmIw";
        var authorizationHeader = OpenIDAuthModule.generateAuthorizationHeader(clientId, clientSecret);
        assertEquals("Basic czZCaGRSa3F0Mzo3RmpmcDBaQnIxS3REUmJuZlZkbUl3", authorizationHeader);
    }

    @Test
    public void testAuthorizationEncoding_specialChars() {
        // Example from https://backstage.forgerock.com/docs/am/7/oauth2-guide/client-auth-header.html
        var clientId = "example.com";
        var clientSecret = "s=cr%t";
        var authorizationHeader = OpenIDAuthModule.generateAuthorizationHeader(clientId, clientSecret);
        assertEquals("Basic ZXhhbXBsZS5jb206cyUzRGNyJTI1dA==", authorizationHeader);
    }
}
