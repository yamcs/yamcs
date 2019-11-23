package org.yamcs.client;

import java.util.Date;
import java.util.Map;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;

/**
 * Contains the authorization state for an identified user or service account.
 */
public class Credentials {

    private String accessToken;
    private String refreshToken;
    private Date expiry;

    Credentials(Map<String, Object> tokenResponse) {
        this.accessToken = (String) tokenResponse.get("access_token");
        this.refreshToken = (String) tokenResponse.get("refresh_token");
        int ttl = ((Number) tokenResponse.get("expires_in")).intValue();
        expiry = new Date(new Date().getTime() + (ttl * 1000));
    }

    Credentials(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public boolean isExpired() {
        return expiry != null && new Date().getTime() >= expiry.getTime();
    }

    public void modifyRequest(HttpRequest request) {
        request.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer " + accessToken);
    }
}
