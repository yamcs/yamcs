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
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.yamcs.security.JwksVerifier.JwtVerificationException;
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
    private static final long DISCOVERY_RETRY_INTERVAL = 30_000;
    private static final int HTTP_TIMEOUT = 10_000;

    private static final String BACKCHANNEL_LOGOUT_EVENT = "http://schemas.openid.net/event/backchannel-logout";
    private static final long CLOCK_SKEW = 60; // seconds

    private OpenIDBackChannelHandler backChannelHandler;

    private String clientId;
    private String clientSecret;
    private String issuer;
    private String authorizationEndpoint;
    private String tokenEndpoint;
    private String endSessionEndpoint;
    private String jwksUri;
    private String scope;

    private String[] nameAttributes;
    private String[] displayNameAttributes;
    private String[] emailAttributes;

    private boolean verifyTls;

    // Provider metadata obtained through OpenID Connect Discovery (only if issuer is set)
    private volatile ProviderMetadata metadata;
    private volatile long lastDiscoveryAttempt;
    private final AtomicBoolean discoveryInProgress = new AtomicBoolean();

    // Verifies tokens signed by the OpenID server (created once the JWKS URI is known)
    private JwksVerifier jwksVerifier;

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
        spec.addOption("issuer", OptionType.STRING);
        spec.addOption("authorizationEndpoint", OptionType.STRING);
        spec.addOption("tokenEndpoint", OptionType.STRING);
        spec.addOption("endSessionEndpoint", OptionType.STRING);
        spec.addOption("jwksUri", OptionType.STRING);
        spec.addOption("clientId", OptionType.STRING).withRequired(true);
        spec.addOption("clientSecret", OptionType.STRING).withRequired(true).withSecret(true);
        spec.addOption("scope", OptionType.STRING).withDefault("openid profile email");
        spec.addOption("attributes", OptionType.MAP).withSpec(attributesSpec)
                .withApplySpecDefaults(true);
        spec.addOption("verifyTls", OptionType.BOOLEAN).withDefault(true);

        // Endpoints are either discovered from the issuer, or configured explicitly
        spec.requireOneOf("issuer", "authorizationEndpoint");
        spec.requireOneOf("issuer", "tokenEndpoint");

        return spec;
    }

    @Override
    public void init(YConfiguration args) throws InitException {
        issuer = args.getString("issuer", null);
        authorizationEndpoint = args.getString("authorizationEndpoint", null);
        tokenEndpoint = args.getString("tokenEndpoint", null);
        endSessionEndpoint = args.getString("endSessionEndpoint", null);
        jwksUri = args.getString("jwksUri", null);
        scope = args.getString("scope");
        clientId = args.getString("clientId");
        clientSecret = args.getString("clientSecret");

        YConfiguration attributesArgs = args.getConfig("attributes");
        nameAttributes = attributesArgs.getList("name").toArray(new String[0]);
        displayNameAttributes = attributesArgs.getList("displayName").toArray(new String[0]);
        emailAttributes = attributesArgs.getList("email").toArray(new String[0]);

        verifyTls = args.getBoolean("verifyTls");

        if (issuer != null) {
            lastDiscoveryAttempt = System.currentTimeMillis();
            metadata = fetchMetadata();
        }

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

        var tokenEndpoint = getTokenEndpoint();
        if (tokenEndpoint == null) {
            throw new AuthenticationException("Token endpoint of OpenID server is not available");
        }

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

                if (issuer != null) {
                    var iss = claims.has("iss") ? claims.get("iss").getAsString() : null;
                    if (!issuer.equals(iss)) {
                        throw new AuthenticationException("Unexpected issuer in ID Token: " + iss);
                    }
                }

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
        var tokenEndpoint = getTokenEndpoint();
        if (tokenEndpoint == null) {
            log.error("Failed to refresh: token endpoint of OpenID server is not available");
            return false;
        }

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
     * Validates a Logout Token received through OpenID Connect Back-Channel Logout, and logs out the matching Yamcs
     * sessions.
     */
    void handleLogoutToken(String logoutToken) throws JwtVerificationException {
        var verifier = getJwksVerifier();
        if (verifier == null) {
            throw new JwtVerificationException("Back-channel logout requires jwksUri or issuer to be configured");
        }

        var claims = verifier.verify(logoutToken);
        validateLogoutClaims(claims, issuer, clientId, System.currentTimeMillis() / 1000);

        var iss = claims.get("iss").getAsString();
        var sid = getStringOrNull(claims, "sid");
        if (sid != null) {
            log.debug("Back-channel logout for sid={}", sid);
            logoutByOidcSessionId(iss, sid);
        } else {
            var sub = getStringOrNull(claims, "sub");
            log.debug("Back-channel logout for sub={}", sub);
            logoutByOidcSubject(iss, sub);
        }
    }

    /**
     * Validates the claims of a Logout Token, as required by OpenID Connect Back-Channel Logout 1.0, section 2.6.
     * 
     * @param now
     *            current time, in seconds since epoch
     */
    static void validateLogoutClaims(JsonObject claims, String issuer, String clientId, long now)
            throws JwtVerificationException {
        var iss = getStringOrNull(claims, "iss");
        if (iss == null) {
            throw new JwtVerificationException("Missing iss");
        }
        if (issuer != null && !issuer.equals(iss)) {
            throw new JwtVerificationException("Unexpected iss: " + iss);
        }

        if (!hasAudience(claims, clientId)) {
            throw new JwtVerificationException("Token is not intended for this client");
        }

        var exp = getLongOrNull(claims, "exp");
        if (exp == null) {
            throw new JwtVerificationException("Missing exp");
        }
        if (now > exp + CLOCK_SKEW) {
            throw new JwtVerificationException("Token expired");
        }
        var iat = getLongOrNull(claims, "iat");
        if (iat == null) {
            throw new JwtVerificationException("Missing iat");
        }
        if (iat > now + CLOCK_SKEW) {
            throw new JwtVerificationException("Token issued in the future");
        }

        if (getStringOrNull(claims, "sub") == null && getStringOrNull(claims, "sid") == null) {
            throw new JwtVerificationException("Missing sub or sid");
        }

        var events = claims.get("events");
        if (events == null || !events.isJsonObject() || !events.getAsJsonObject().has(BACKCHANNEL_LOGOUT_EVENT)) {
            throw new JwtVerificationException("Missing back-channel logout event");
        }

        if (claims.has("nonce")) {
            throw new JwtVerificationException("Logout Token must not contain a nonce");
        }
    }

    private static boolean hasAudience(JsonObject claims, String clientId) {
        var aud = claims.get("aud");
        if (aud == null) {
            return false;
        } else if (aud.isJsonArray()) {
            for (var el : aud.getAsJsonArray()) {
                if (el.isJsonPrimitive() && clientId.equals(el.getAsString())) {
                    return true;
                }
            }
            return false;
        } else {
            return aud.isJsonPrimitive() && clientId.equals(aud.getAsString());
        }
    }

    private static Long getLongOrNull(JsonObject obj, String key) {
        var el = obj.get(key);
        if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            return el.getAsLong();
        }
        return null;
    }

    private synchronized JwksVerifier getJwksVerifier() {
        if (jwksVerifier == null) {
            var uri = getJwksUri();
            if (uri != null) {
                jwksVerifier = new JwksVerifier(() -> fetchJson(uri));
            }
        }
        return jwksVerifier;
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

    /**
     * Returns the authorization endpoint, either configured or discovered. Returns null if discovery has not yet
     * succeeded.
     */
    public String getAuthorizationEndpoint() {
        if (authorizationEndpoint != null) {
            return authorizationEndpoint;
        }
        var metadata = resolveMetadata();
        return metadata != null ? metadata.authorizationEndpoint() : null;
    }

    private String getTokenEndpoint() {
        if (tokenEndpoint != null) {
            return tokenEndpoint;
        }
        var metadata = resolveMetadata();
        return metadata != null ? metadata.tokenEndpoint() : null;
    }

    public String getScope() {
        return scope;
    }

    /**
     * Returns the end session endpoint, either configured or discovered. Returns null if the OpenID server does not
     * support RP-Initiated Logout, or if discovery has not yet succeeded.
     */
    public String getEndSessionEndpoint() {
        if (endSessionEndpoint != null) {
            return endSessionEndpoint;
        }
        var metadata = resolveMetadata();
        return metadata != null ? metadata.endSessionEndpoint() : null;
    }

    /**
     * Returns the URL of the JSON Web Key Set of the OpenID server, either configured or discovered. Returns null if
     * not configured, or if discovery has not yet succeeded.
     */
    public String getJwksUri() {
        if (jwksUri != null) {
            return jwksUri;
        }
        var metadata = resolveMetadata();
        return metadata != null ? metadata.jwksUri() : null;
    }

    /**
     * Returns the URL where to redirect the browser for ending the session at the OpenID server (RP-Initiated Logout),
     * or null if no end session endpoint is available.
     */
    public String buildEndSessionUrl(OpenIDAuthenticationInfo info) {
        var endSessionEndpoint = getEndSessionEndpoint();
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

    /**
     * Returns the metadata of the OpenID server, as obtained through OpenID Connect Discovery. Returns null if no
     * issuer is configured, or if discovery has not yet succeeded. Failed attempts are retried in the background, but
     * not more often than every {@link #DISCOVERY_RETRY_INTERVAL}.
     */
    private ProviderMetadata resolveMetadata() {
        if (issuer == null || metadata != null) {
            return metadata;
        }

        // Retry in the background, callers may be on an I/O thread
        var now = System.currentTimeMillis();
        if (now - lastDiscoveryAttempt >= DISCOVERY_RETRY_INTERVAL && discoveryInProgress.compareAndSet(false, true)) {
            lastDiscoveryAttempt = now;
            var thread = new Thread(() -> {
                try {
                    var result = fetchMetadata();
                    if (result != null) {
                        metadata = result;
                    }
                } finally {
                    discoveryInProgress.set(false);
                }
            }, "OpenIDDiscovery");
            thread.setDaemon(true);
            thread.start();
        }
        return metadata;
    }

    private ProviderMetadata fetchMetadata() {
        var url = discoveryUrl(issuer);
        try {
            JsonObject response = fetchJson(url);

            var discoveredIssuer = getStringOrNull(response, "issuer");
            if (!issuer.equals(discoveredIssuer)) {
                log.warn("Ignoring OpenID provider metadata from {}: issuer '{}' does not match '{}'",
                        url, discoveredIssuer, issuer);
                return null;
            }

            var result = new ProviderMetadata(
                    getStringOrNull(response, "authorization_endpoint"),
                    getStringOrNull(response, "token_endpoint"),
                    getStringOrNull(response, "end_session_endpoint"),
                    getStringOrNull(response, "jwks_uri"));
            log.debug("Discovered OpenID provider metadata: {}", result);
            return result;
        } catch (Exception e) {
            log.warn("Failed to retrieve OpenID provider metadata from {}: {}", url, e.toString());
            return null;
        }
    }

    /**
     * Retrieves a JSON document from the OpenID server.
     */
    private JsonObject fetchJson(String url) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = openConnection(url);
            conn.setConnectTimeout(HTTP_TIMEOUT);
            conn.setReadTimeout(HTTP_TIMEOUT);

            int statusCode = conn.getResponseCode();
            if (statusCode != 200) {
                throw new IOException("HTTP " + statusCode + " from " + url);
            }
            return readJson(conn.getInputStream());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IOException("Failed to configure HTTPS connection", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
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

    private static String getStringOrNull(JsonObject obj, String key) {
        var el = obj.get(key);
        return (el != null && el.isJsonPrimitive()) ? el.getAsString() : null;
    }

    private static String discoveryUrl(String issuer) {
        var base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        return base + "/.well-known/openid-configuration";
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

    private record ProviderMetadata(String authorizationEndpoint, String tokenEndpoint,
            String endSessionEndpoint, String jwksUri) {
    }
}
