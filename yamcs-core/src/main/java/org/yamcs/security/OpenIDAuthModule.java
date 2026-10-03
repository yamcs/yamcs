package org.yamcs.security;

import static com.google.common.collect.Multimaps.synchronizedMultimap;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.HttpsURLConnection;

import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.http.HttpServer;
import org.yamcs.http.auth.JwtHelper;
import org.yamcs.http.auth.JwtHelper.JwtDecodeException;
import org.yamcs.logging.Log;
import org.yamcs.security.OpenIDAuthenticationInfo.ExternalClaim;
import org.yamcs.security.OpenIDAuthenticationInfo.ExternalSession;
import org.yamcs.security.OpenIDAuthenticationInfo.ExternalSubject;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * AuthModule that identifies users against an external identity provider compliant with OpenID Connect (OIDC).
 * <p>
 * See https://openid.net/connect/
 */
public class OpenIDAuthModule implements AuthModule, SessionListener {

    private static final Log log = new Log(OpenIDAuthModule.class);
    private OpenIDBackChannelHandler backChannelHandler;

    private String clientId;
    private String clientSecret;
    private String authorizationEndpoint;
    private String tokenEndpoint;
    private String endSessionEndpoint;
    private String scope;

    private String[] nameAttributes;
    private String[] displayNameAttributes;
    private String[] emailAttributes;

    private boolean verifyTls;

    // Map external sub and/or sid to Yamcs sessions.
    // This structure allows handling OIDC backchannel logout requests
    private Multimap<ExternalClaim, UserSession> sessionsByClaim = synchronizedMultimap(
            ArrayListMultimap.create());

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
        spec.addOption("endSessionEndpoint", OptionType.STRING);
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
        endSessionEndpoint = args.getString("endSessionEndpoint", null);
        scope = args.getString("scope");
        clientId = args.getString("clientId");
        clientSecret = args.getString("clientSecret");

        YConfiguration attributesArgs = args.getConfig("attributes");
        nameAttributes = attributesArgs.getList("name").toArray(new String[0]);
        displayNameAttributes = attributesArgs.getList("displayName").toArray(new String[0]);
        emailAttributes = attributesArgs.getList("email").toArray(new String[0]);

        verifyTls = args.getBoolean("verifyTls");

        backChannelHandler = new OpenIDBackChannelHandler(this);

        var httpServer = YamcsServer.getServer().getGlobalService(HttpServer.class);
        httpServer.addRoute("openid", () -> backChannelHandler);
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

        HttpURLConnection conn = null;
        try {
            conn = openConnection(tokenEndpoint);

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            var authorizationHeader = generateAuthorizationHeader(clientId, clientSecret);
            conn.setRequestProperty("Authorization", authorizationHeader);

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
                JsonObject response = readJson(conn.getInputStream());

                String idToken = response.get("id_token").getAsString();
                String accessToken = response.get("access_token").getAsString();
                JsonObject claims = JwtHelper.decodeUnverified(idToken);

                var refreshTokenElement = response.get("refresh_token");
                String refreshToken = (refreshTokenElement != null) ? refreshTokenElement.getAsString() : null;

                String username = findAttribute(claims, nameAttributes);

                var authInfo = new OpenIDAuthenticationInfo(
                        this, redirectUri, idToken, accessToken, refreshToken, username, claims);
                authInfo.setEmail(findAttribute(claims, emailAttributes));
                authInfo.setDisplayName(findAttribute(claims, displayNameAttributes));
                return authInfo;
            } else {
                throw new AuthenticationException(readErrorBody(conn));
            }
        } catch (IOException | JwtDecodeException | JsonParseException e) {
            throw new AuthenticationException(e.getMessage(), e);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new AuthenticationException("Failed to configure HTTPS connection", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
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
        HttpURLConnection conn = null;
        try {
            conn = openConnection(tokenEndpoint);

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            var authorizationHeader = generateAuthorizationHeader(clientId, clientSecret);
            conn.setRequestProperty("Authorization", authorizationHeader);

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
                JsonObject response = readJson(conn.getInputStream());

                info.idToken = response.get("id_token").getAsString();
                info.accessToken = response.get("access_token").getAsString();
                var claims = JwtHelper.decodeUnverified(info.idToken);
                info.expiresAt = claims.get("exp").getAsLong() * 1000L;

                var refreshTokenElement = response.get("refresh_token");
                info.refreshToken = (refreshTokenElement != null) ? refreshTokenElement.getAsString() : null;
            } else {
                log.error("Received error from identity provider: " + readErrorBody(conn));
                return false;
            }

            return true;
        } catch (IOException | NoSuchAlgorithmException | KeyManagementException | JwtDecodeException
                | JsonParseException e) {
            log.error("Failed to refresh", e);
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @Override
    public void onCreated(UserSession session) {
        if (session.getAuthenticationInfo() instanceof OpenIDAuthenticationInfo) {
            var authInfo = (OpenIDAuthenticationInfo) session.getAuthenticationInfo();

            sessionsByClaim.put(authInfo.getIssuerSub(), session);
            var issuerSid = authInfo.getIssuerSid();
            if (issuerSid != null) {
                sessionsByClaim.put(issuerSid, session);
            }
        }
    }

    @Override
    public void onExpired(UserSession session) {
        if (session.getAuthenticationInfo() instanceof OpenIDAuthenticationInfo) {
            var authInfo = (OpenIDAuthenticationInfo) session.getAuthenticationInfo();

            var sub = authInfo.getIssuerSub();
            sessionsByClaim.removeAll(sub);
            var sid = authInfo.getIssuerSid();
            if (sid != null) {
                sessionsByClaim.removeAll(sid);
            }
        }
    }

    @Override
    public void onInvalidated(UserSession session) {
        if (session.getAuthenticationInfo() instanceof OpenIDAuthenticationInfo) {
            var authInfo = (OpenIDAuthenticationInfo) session.getAuthenticationInfo();

            var sub = authInfo.getIssuerSub();
            sessionsByClaim.removeAll(sub);
            var sid = authInfo.getIssuerSid();
            if (sid != null) {
                sessionsByClaim.removeAll(sid);
            }
        }
    }

    /**
     * Log out all Yamcs sessions for the provided OpenID subject.
     */
    public void logoutByOidcSubject(String iss, String sub) {
        var sessions = sessionsByClaim.get(new ExternalSubject(iss, sub));

        var sessionIds = new HashSet<String>();
        synchronized (sessionsByClaim) {
            sessions.forEach(session -> sessionIds.add(session.getId()));
        }

        var sessionManager = YamcsServer.getServer().getSecurityStore().getSessionManager();
        for (var sessionId : sessionIds) {
            sessionManager.invalidateSession(sessionId);
        }
    }

    /**
     * Log out all Yamcs sessions for the provided OpenID session.
     */
    public void logoutByOidcSessionId(String iss, String sid) {
        var sessions = sessionsByClaim.get(new ExternalSession(iss, sid));

        var sessionIds = new HashSet<String>();
        synchronized (sessionsByClaim) {
            sessions.forEach(session -> sessionIds.add(session.getId()));
        }

        var sessionManager = YamcsServer.getServer().getSecurityStore().getSessionManager();
        for (var sessionId : sessionIds) {
            sessionManager.invalidateSession(sessionId);
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

    public String getEndSessionEndpoint() {
        return endSessionEndpoint;
    }

    /**
     * Returns the URL where to redirect the browser for ending the session at the OpenID server (RP-Initiated Logout),
     * or null if no {@code endSessionEndpoint} is configured.
     */
    public String buildEndSessionUrl(OpenIDAuthenticationInfo info) {
        if (endSessionEndpoint == null) {
            return null;
        }

        var params = new LinkedHashMap<String, String>();
        if (info.idToken != null) {
            params.put("id_token_hint", info.idToken);
        }
        params.put("client_id", clientId);
        if (info.redirectUri != null) {
            // Return to the root of the web application that initiated the login
            var postLogoutRedirectUri = URI.create(info.redirectUri).resolve(".");
            params.put("post_logout_redirect_uri", postLogoutRedirectUri.toString());
        }

        var separator = endSessionEndpoint.contains("?") ? "&" : "?";
        return endSessionEndpoint + separator + new String(encodeRequestBody(params), UTF_8);
    }

    private static JsonObject readJson(InputStream stream) throws IOException {
        try (Reader in = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
            var json = new Gson().fromJson(in, JsonObject.class);
            if (json == null) {
                throw new JsonParseException("Empty response");
            }
            return json;
        }
    }

    /**
     * Reads the body of an error response, for inclusion in error messages.
     */
    private static String readErrorBody(HttpURLConnection conn) throws IOException {
        var stream = conn.getErrorStream();
        if (stream == null) {
            return "HTTP " + conn.getResponseCode();
        }
        try (stream) {
            return "HTTP " + conn.getResponseCode() + ": " + new String(stream.readAllBytes(), UTF_8);
        }
    }

    private HttpURLConnection openConnection(String url)
            throws IOException, NoSuchAlgorithmException, KeyManagementException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        if (!verifyTls && (conn instanceof HttpsURLConnection)) {
            HttpsUrlConnectionUtils.makeInsecure((HttpsURLConnection) conn);
        }
        return conn;
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
}
