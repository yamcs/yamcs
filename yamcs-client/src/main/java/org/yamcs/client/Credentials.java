package org.yamcs.client;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;

/**
 * Contains the authorization state for an identified user or service account.
 */
public class Credentials {

    private String accessToken;
    private String refreshToken;
    private Date expiry;

    public Credentials(String accessToken, String refreshToken) {
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

    public static Credentials fromJsonTokenResponse(String json) {
        Type gsonType = new TypeToken<Map<String, Object>>() {
        }.getType();
        Map<String, Object> map = new Gson().fromJson(json, gsonType);
        String accessToken = (String) map.get("access_token");
        String refreshToken = (String) map.get("refresh_token");
        Credentials credentials = new Credentials(accessToken, refreshToken);

        int ttl = ((Number) map.get("expires_in")).intValue();
        credentials.expiry = new Date(new Date().getTime() + (ttl * 1000));
        return credentials;
    }
}
