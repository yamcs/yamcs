package org.yamcs.http;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.yamcs.YamcsServer;
import org.yamcs.http.JwtHelper.JwtDecodeException;
import org.yamcs.logging.Log;
import org.yamcs.security.AuthenticationInfo;
import org.yamcs.security.CryptoUtils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Store capable of generating a chain of refresh tokens. When a token is exchanged for a new token, the old token
 * remains valid for a limited lifetime. This property is useful do deal with a burst of identical refresh requests.
 * <p>
 * This class maintains a cache from a JWT bearer token to the original authentication info. This allows skipping the
 * login process as long as the bearer is valid.
 */
public class TokenStore {

    private static final Log log = new Log(TokenStore.class);

    private final ConcurrentHashMap<String, AuthenticationInfo> accessTokens = new ConcurrentHashMap<>();
    private int cleaningCounter = 0;

    private Map<Hmac, AuthenticationInfo> refreshTokens = new HashMap<>();

    private Cache<Hmac, RefreshResult> refreshCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .build();

    public void registerAccessToken(String accessToken, AuthenticationInfo authenticationInfo) {
        accessTokens.put(accessToken, authenticationInfo);
    }

    public void revokeAccessToken(String accessToken) {
        accessTokens.remove(accessToken);
    }

    public AuthenticationInfo verifyAccessToken(String accessToken) throws UnauthorizedException {
        cleaningCounter++;
        if (cleaningCounter > 1000) {
            cleaningCounter = 0;
            forgetExpiredAccessTokens();
        }
        try {
            JwtToken jwtToken = new JwtToken(accessToken, YamcsServer.getServer().getSecretKey());
            if (jwtToken.isExpired()) {
                accessTokens.remove(accessToken);
                throw new UnauthorizedException("Token expired");
            }
            AuthenticationInfo authenticationInfo = accessTokens.get(accessToken);
            if (authenticationInfo == null) {
                log.warn("Got an invalid access token");
                throw new UnauthorizedException("Invalid access token");
            }

            return authenticationInfo;
        } catch (JwtDecodeException e) {
            throw new UnauthorizedException("Failed to decode JWT: " + e.getMessage());
        }
    }

    private void forgetExpiredAccessTokens() {
        for (String accessToken : accessTokens.keySet()) {
            try {
                JwtToken jwtToken = new JwtToken(accessToken, YamcsServer.getServer().getSecretKey());
                if (jwtToken.isExpired()) {
                    accessTokens.remove(accessToken);
                }
            } catch (JwtDecodeException e) {
                accessTokens.remove(accessToken);
            }
        }
    }

    public synchronized String generateRefreshToken(AuthenticationInfo authenticationInfo) {
        String refreshToken = UUID.randomUUID().toString();
        Hmac hmac = new Hmac(refreshToken);
        refreshTokens.put(hmac, authenticationInfo);
        return refreshToken;
    }

    /**
     * Validate the provided refresh token, and exchange it for a new one. The provided refresh token is invalidated,
     * and will stop working after a certain time.
     * <p>
     * Attempts to exchange a previously exchanged token will always return the same result, as long as it has not
     * expired yet.
     * 
     * @return a new refresh token, or null if the token could not be exchanged.
     */
    public synchronized RefreshResult verifyRefreshToken(String refreshToken) {
        Hmac hmac = new Hmac(refreshToken);
        AuthenticationInfo authenticationInfo = refreshTokens.get(hmac);
        if (authenticationInfo != null) { // Token valid, generate new token (once only)
            String nextToken = generateRefreshToken(authenticationInfo);
            RefreshResult result = new RefreshResult(authenticationInfo, nextToken);
            refreshCache.put(hmac, result);
            refreshTokens.remove(hmac);
            return result;
        } else { // Maybe an old token, attempt to upgrade it based on previous token exchanges
            RefreshResult result = null;
            RefreshResult candidate = refreshCache.getIfPresent(hmac);
            while (candidate != null) {
                result = candidate;
                candidate = refreshCache.getIfPresent(new Hmac(candidate.refreshToken));
            }
            return result;
        }
    }

    public synchronized void revokeRefreshToken(String refreshToken) {
        Hmac hmac = new Hmac(refreshToken);
        refreshTokens.remove(hmac);
        refreshCache.invalidate(hmac);
    }

    /**
     * byte[] wrapper that allows value comparison in HashMap
     */
    private static final class Hmac {

        private byte[] hmac;

        Hmac(String refreshToken) {
            hmac = CryptoUtils.calculateHmac(refreshToken, YamcsServer.getServer().getSecretKey());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof Hmac)) {
                return false;
            }
            return Arrays.equals(hmac, ((Hmac) obj).hmac);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(hmac);
        }
    }

    static final class RefreshResult {
        AuthenticationInfo authenticationInfo;
        String refreshToken;

        RefreshResult(AuthenticationInfo authenticationInfo, String refreshToken) {
            this.authenticationInfo = authenticationInfo;
            this.refreshToken = refreshToken;
        }
    }
}
