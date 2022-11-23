package org.yamcs.client;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.client.base.SpnegoInfo;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;

/**
 * Contains the authorization state for an identified user or service account.
 */
public class OAuth2Credentials implements Credentials {

    // Matches patterns of the form:
    // "key": "value",
    // "key": "value"}
    // "key": value,
    // "key": value}
    private static final Pattern KEY_VALUE = Pattern.compile("\"(\\w+)\"\\s*\\:\\s*\"?([^\",]*)\"?\\s*[,\\}]");

    private String tokenResponse;
    private String accessToken;
    private String refreshToken;
    private Date expiry;

    // We keep this around for when we need to acquire a new access token
    // (SPNEGO connections do not get refreshed using oauth, so that the TGT can be reconfirmed)
    private SpnegoInfo spnegoInfo;

    public OAuth2Credentials(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    /**
     * Returns a JSON string with the full unmodified token response.
     */
    public String getTokenResponse() {
        return tokenResponse;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public SpnegoInfo getSpnegoInfo() {
        return spnegoInfo;
    }

    public void setSpnegoInfo(SpnegoInfo spnegoInfo) {
        this.spnegoInfo = spnegoInfo;
    }

    @Override
    public boolean isExpired() {
        return expiry != null && new Date().getTime() >= expiry.getTime();
    }

    @Override
    public void modifyRequest(HttpRequest request) {
        request.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer " + accessToken);
    }

    public static OAuth2Credentials fromJsonTokenResponse(String json) {
        Map<String, String> map = toMap(json);
        String accessToken = map.get("access_token");
        String refreshToken = map.get("refresh_token");
        var credentials = new OAuth2Credentials(accessToken, refreshToken);

        int ttl = Integer.valueOf(map.get("expires_in"));
        credentials.expiry = new Date(new Date().getTime() + (ttl * 1000));
        credentials.tokenResponse = json;
        return credentials;
    }

    private static Map<String, String> toMap(String json) {
        // Use just a simple regex because we prefer not to force a full-blown JSON library
        // as a dependency of yamcs-client.
        Map<String, String> map = new HashMap<>();
        Matcher matcher = KEY_VALUE.matcher(json);
        while (matcher.find()) {
            map.put(matcher.group(1), matcher.group(2));
        }
        return map;
    }
}
