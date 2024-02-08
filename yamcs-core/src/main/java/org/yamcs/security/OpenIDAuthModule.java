package org.yamcs.security;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.HttpsURLConnection;

import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.http.auth.JwtHelper;
import org.yamcs.http.auth.JwtHelper.JwtDecodeException;
import org.yamcs.logging.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * AuthModule that identifies users against an external identity provider compliant with OpenID Connect (OIDC).
 * <p>
 * See https://openid.net/connect/
 */
public class OpenIDAuthModule implements AuthModule {

    private static final Log log = new Log(OpenIDAuthModule.class);

    private String clientId;
    private String clientSecret;
    private String authorizationEndpoint;
    private String tokenEndpoint;
    private String scope;

    private String[] nameAttributes;
    private String[] displayNameAttributes;
    private String[] emailAttributes;

    private boolean verifyTls;

    @Override
    public Spec getSpec() {
        Spec attributesSpec = new Spec();
        attributesSpec.addOption("name", OptionType.LIST_OR_ELEMENT)
                .withElementType(OptionType.STRING)
                .withDefault(Arrays.asList("preferred_username", "nickname", "email"));
        attributesSpec.addOption("email", OptionType.LIST_OR_ELEMENT)
                .withElementType(OptionType.STRING)
                .withDefault("email");
        attributesSpec.addOption("displayName", OptionType.LIST_OR_ELEMENT)
                .withElementType(OptionType.STRING)
                .withDefault("name");

        Spec spec = new Spec();
        spec.addOption("authorizationEndpoint", OptionType.STRING).withRequired(true);
        spec.addOption("tokenEndpoint", OptionType.STRING).withRequired(true);
        spec.addOption("clientId", OptionType.STRING).withRequired(true);
        spec.addOption("clientSecret", OptionType.STRING).withRequired(true).withSecret(true);
        spec.addOption("scope", OptionType.STRING).withDefault("openid profile email");
        spec.addOption("attributes", OptionType.MAP).withSpec(attributesSpec)
                .withApplySpecDefaults(true);
        spec.addOption("verifyTls", OptionType.BOOLEAN).withDefault(true);

        return spec;
    }

    @Override
    public void init(YConfiguration args) throws InitException {
        authorizationEndpoint = args.getString("authorizationEndpoint");
        tokenEndpoint = args.getString("tokenEndpoint");
        scope = args.getString("scope");
        clientId = args.getString("clientId");
        clientSecret = args.getString("clientSecret");

        YConfiguration attributesArgs = args.getConfig("attributes");
        nameAttributes = attributesArgs.getList("name").toArray(new String[0]);
        displayNameAttributes = attributesArgs.getList("displayName").toArray(new String[0]);
        emailAttributes = attributesArgs.getList("email").toArray(new String[0]);

        verifyTls = args.getBoolean("verifyTls");
    }

    @Override
    public AuthenticationInfo getAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        if (token instanceof ThirdPartyAuthorizationCode) {
            String code = ((ThirdPartyAuthorizationCode) token).getPrincipal();
            if (code.startsWith("oidc ")) {
                String jwt = code.substring(5);
                try {
                    JsonObject clientInfo = JwtHelper.decodeUnverified(jwt);
                    return authenticateByCode(clientInfo);
                } catch (JwtDecodeException e) {
                    throw new AuthenticationException("Invalid JWT", e);
                }
            }
        }
        return null;
    }

    private AuthenticationInfo authenticateByCode(JsonObject clientInfo) throws AuthenticationException {
        String oidcCode = clientInfo.get("code").getAsString();
        String redirectUri = clientInfo.get("redirect_uri").getAsString();

        try {
            URL url = new URL(tokenEndpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            var authorizationHeader = generateAuthorizationHeader(clientId, clientSecret);
            conn.setRequestProperty("Authorization", authorizationHeader);

            if (!verifyTls && (conn instanceof HttpsURLConnection)) {
                try {
                    HttpsUrlConnectionUtils.makeInsecure((HttpsURLConnection) conn);
                } catch (NoSuchAlgorithmException | KeyManagementException e) {
                    throw new AuthenticationException("Failed to configure HTTPS connection", e);
                }
            }

            Map<String, String> formData = new HashMap<>();
            formData.put("grant_type", "authorization_code");
            formData.put("code", oidcCode);

            // OIDC requires the same redirect_uri to be used as was used to get the code from
            // the authorization endpoint. There's not actually a redirect going to happen.
            formData.put("redirect_uri", redirectUri);

            conn.setDoOutput(true);
            byte[] b = encodeRequestBody(formData);
            conn.getOutputStream().write(b);

            int statusCode = conn.getResponseCode();
            if (statusCode == 200) {
                Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), UTF_8));
                JsonObject response = new Gson().fromJson(in, JsonObject.class);

                String idToken = response.get("id_token").getAsString();
                String accessToken = response.get("access_token").getAsString();
                JsonObject claims = JwtHelper.decodeUnverified(idToken);

                var refreshTokenElement = response.get("refresh_token");
                String refreshToken = (refreshTokenElement != null) ? refreshTokenElement.getAsString() : null;

                return createAuthenticationInfo(redirectUri, idToken, accessToken, refreshToken, claims);
            } else {
                Reader in = new BufferedReader(new InputStreamReader(conn.getErrorStream(), UTF_8));
                JsonObject response = new Gson().fromJson(in, JsonObject.class);
                throw new AuthenticationException(response.toString());
            }
        } catch (IOException | JwtDecodeException e) {
            throw new AuthenticationException(e.getMessage(), e);
        }
    }

    private OpenIDAuthenticationInfo createAuthenticationInfo(String redirectUri,
            String idToken, String accessToken, String refreshToken, JsonObject claims) {
        String username = findAttribute(claims, nameAttributes);

        var authInfo = new OpenIDAuthenticationInfo(
                this, redirectUri, idToken, accessToken, refreshToken, username);
        authInfo.expiresAt = claims.get("exp").getAsLong() * 1000L;
        authInfo.setEmail(findAttribute(claims, emailAttributes));
        authInfo.setDisplayName(findAttribute(claims, displayNameAttributes));

        // According to OpenID spec, only the combination of "iss" with "sub" is a
        // reasonably unique identifier.
        JsonObject externalId = new JsonObject();
        externalId.add("iss", claims.get("iss"));
        externalId.add("sub", claims.get("sub"));
        authInfo.addExternalIdentity(getClass().getName(), externalId.toString());

        return authInfo;
    }

    private String findAttribute(JsonObject claims, String[] possibleNames) {
        for (String attrId : possibleNames) {
            JsonElement el = claims.get(attrId);
            if (el != null) {
                return (String) el.getAsString();
            }
        }
        return null;
    }

    @Override
    public AuthorizationInfo getAuthorizationInfo(AuthenticationInfo authenticationInfo) throws AuthorizationException {
        return new AuthorizationInfo();
    }

    @Override
    public boolean verifyValidity(AuthenticationInfo authenticationInfo) {
        if (authenticationInfo instanceof OpenIDAuthenticationInfo) {
            var info = (OpenIDAuthenticationInfo) authenticationInfo;
            var now = System.currentTimeMillis();
            var expired = info.expiresAt > 0 && info.expiresAt < now;
            if (expired && info.refreshToken != null) {
                return refreshToken(info);
            }
            return true; // Only enforce refresh check if we have a refresh token
        }

        return false;
    }

    private boolean refreshToken(OpenIDAuthenticationInfo info) {
        try {
            URL url = new URL(tokenEndpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            var authorizationHeader = generateAuthorizationHeader(clientId, clientSecret);
            conn.setRequestProperty("Authorization", authorizationHeader);

            if (!verifyTls && (conn instanceof HttpsURLConnection)) {
                HttpsUrlConnectionUtils.makeInsecure((HttpsURLConnection) conn);
            }

            Map<String, String> formData = new HashMap<>();
            formData.put("grant_type", "refresh_token");
            formData.put("refresh_token", info.refreshToken);

            // OIDC requires the same redirect_uri to be used as was used to get the code from
            // the authorization endpoint. There's not actually a redirect going to happen.
            formData.put("redirect_uri", info.redirectUri);

            conn.setDoOutput(true);
            byte[] b = encodeRequestBody(formData);
            conn.getOutputStream().write(b);

            int statusCode = conn.getResponseCode();
            if (statusCode == 200) {
                Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), UTF_8));
                JsonObject response = new Gson().fromJson(in, JsonObject.class);

                info.idToken = response.get("id_token").getAsString();
                info.accessToken = response.get("access_token").getAsString();
                var claims = JwtHelper.decodeUnverified(info.idToken);
                info.expiresAt = claims.get("exp").getAsLong() * 1000L;

                var refreshTokenElement = response.get("refresh_token");
                info.refreshToken = (refreshTokenElement != null) ? refreshTokenElement.getAsString() : null;
            } else {
                Reader in = new BufferedReader(new InputStreamReader(conn.getErrorStream(), UTF_8));
                JsonObject response = new Gson().fromJson(in, JsonObject.class);
                log.error("Received error from identity provider: " + response);
                return false;
            }

            return true;
        } catch (IOException | NoSuchAlgorithmException | KeyManagementException | JwtDecodeException e) {
            log.error("Failed to refresh", e);
            return false;
        }
    }

    public String getClientId() {
        return clientId;
    }

    public String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }

    public String getScope() {
        return scope;
    }

    static String generateAuthorizationHeader(String clientId, String clientSecret) {
        // See https://datatracker.ietf.org/doc/html/rfc6749#section-2.3.1
        var encodedClientId = URLEncoder.encode(clientId, UTF_8);
        var encodedSecret = URLEncoder.encode(clientSecret, UTF_8);
        var auth = Base64.getEncoder().encodeToString(
                (encodedClientId + ":" + encodedSecret).getBytes(UTF_8));
        return "Basic " + auth;
    }

    private static byte[] encodeRequestBody(Map<String, String> params) {
        StringBuilder postData = new StringBuilder();
        for (Entry<String, String> param : params.entrySet()) {
            if (postData.length() != 0) {
                postData.append('&');
            }
            postData.append(URLEncoder.encode(param.getKey(), UTF_8));
            postData.append('=');
            postData.append(URLEncoder.encode(param.getValue(), UTF_8));
        }
        return postData.toString().getBytes(UTF_8);
    }

    protected static class OpenIDAuthenticationInfo extends AuthenticationInfo {
        // Make available for extensions that maybe want to add roles.
        public String idToken;
        public String accessToken;
        public String refreshToken;

        // Redirect URI for use in outgoing requests
        public String redirectUri;

        // When the ID Token expires
        public long expiresAt;

        OpenIDAuthenticationInfo(AuthModule authenticator, String redirectUri,
                String idToken, String accessToken,
                String refreshToken, String username) {
            super(authenticator, username);
            this.redirectUri = redirectUri;
            this.idToken = idToken;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }
}
