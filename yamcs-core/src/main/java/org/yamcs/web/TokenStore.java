package org.yamcs.web;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.yamcs.YamcsServer;
import org.yamcs.security.CryptoUtils;
import org.yamcs.security.User;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Store capable of generating a chain of refresh tokens. When a token is exchanged for a new token, the old token
 * remains valid for a limited lifetime. This property is useful do deal with a burst of identical refresh requests.
 * 
 * TODO this should probably work with 'string' usernames instead of 'User' object. Conversion between the two can be a
 * responsibility of security layer, not HTTP.
 */
public class TokenStore {

    private Map<Hmac, User> refreshTokens = new HashMap<>();

    private Cache<Hmac, IdentifyResult> resultCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .build();

    public synchronized String generateRefreshToken(User user) {
        String refreshToken = UUID.randomUUID().toString();
        Hmac hmac = new Hmac(refreshToken);
        refreshTokens.put(hmac, user);
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
    public synchronized IdentifyResult identify(String refreshToken) {
        Hmac hmac = new Hmac(refreshToken);
        User user = refreshTokens.get(hmac);
        if (user != null) { // Token valid, generate new token (once only)
            String nextToken = generateRefreshToken(user);
            IdentifyResult result = new IdentifyResult(user, nextToken);
            resultCache.put(hmac, result);
            refreshTokens.remove(hmac);
            return result;
        } else { // Maybe an old token, attempt to upgrade it based on previous token exchanges
            IdentifyResult result = null;
            IdentifyResult candidate = resultCache.getIfPresent(hmac);
            while (candidate != null) {
                result = candidate;
                candidate = resultCache.getIfPresent(new Hmac(candidate.refreshToken));
            }
            return result;
        }
    }

    public synchronized void revokeRefreshToken(String refreshToken) {
        Hmac hmac = new Hmac(refreshToken);
        refreshTokens.remove(hmac);
        resultCache.invalidate(hmac);
    }

    /**
     * byte[] wrapper that allows value comparison in HashMap
     */
    private static final class Hmac {

        private byte[] hmac;

        Hmac(String refreshToken) {
            hmac = CryptoUtils.calculateHmac(refreshToken, YamcsServer.getSecretKey());
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

    static final class IdentifyResult {
        User user;
        String refreshToken;

        IdentifyResult(User user, String refreshToken) {
            this.user = user;
            this.refreshToken = refreshToken;
        }
    }
}
