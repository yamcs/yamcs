package org.yamcs.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;

public class BasicAuthCredentials implements Credentials {

    private final String authorizationHeader;

    public BasicAuthCredentials(String username, char[] password) {
        authorizationHeader = "Basic " + Base64.getEncoder().encodeToString(
                (username + ":" + new String(password)).getBytes(StandardCharsets.UTF_8));
    }

    public String getAuthorizationHeader() {
        return authorizationHeader;
    }

    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public void modifyRequest(HttpRequest request) {
        request.headers().add(HttpHeaderNames.AUTHORIZATION, authorizationHeader);
    }
}
