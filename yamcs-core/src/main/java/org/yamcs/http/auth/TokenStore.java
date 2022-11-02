package org.yamcs.http.auth;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.yamcs.InitException;
import org.yamcs.YamcsServer;
import org.yamcs.http.AbstractHttpService;
import org.yamcs.http.HttpServer;
import org.yamcs.http.UnauthorizedException;
import org.yamcs.http.auth.JwtHelper.JwtDecodeException;
import org.yamcs.security.AuthenticationInfo;
import org.yamcs.security.CryptoUtils;
import org.yamcs.security.SecurityStore;
import org.yamcs.security.SessionExpiredException;
import org.yamcs.security.SessionManager;
import org.yamcs.security.UserSession;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Store capable of generating a chain of refresh tokens. When a token is exchanged for a new token, the old token
 * remains valid for a limited lifetime. This property is useful do deal with a burst of identical refresh requests.
 * <p>
 * This class maintains a cache from a JWT bearer token to the original authentication info. This allows skipping the
 * login process as long as the bearer is valid.
 */
public class TokenStore extends AbstractHttpService {

    private final ConcurrentHashMap<String, AuthenticationInfo> accessTokens = new ConcurrentHashMap<>();
    private int cleaningCounter = 0;

    private Map<Hmac, RefreshState> refreshTokens = new HashMap<>();

    private Cache<Hmac, RefreshResult> refreshCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .build();

    @Override
    public void init(HttpServer httpServer) throws InitException {
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        accessTokens.clear();
        refreshTokens.clear();
        refreshCache.invalidateAll();
        cleaningCounter = 0;
        notifyStopped();
    }

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
        accessTokens.entrySet().removeIf(entry -> {
            try {
                JwtToken jwtToken = new JwtToken(entry.getKey(), YamcsServer.getServer().getSecretKey());
                return jwtToken.isExpired();
            } catch (JwtDecodeException e) {
                return true;
            }
        });
    }

    public synchronized void forgetUser(String username) {
        refreshTokens.entrySet().removeIf(entry -> {
            return username.equals(entry.getValue().authenticationInfo.getUsername());
        });
        accessTokens.entrySet().removeIf(entry -> {
            return username.equals(entry.getValue().getUsername());
        });
    }

    public synchronized String generateRefreshToken(AuthenticationInfo authenticationInfo, UserSession session) {
        String refreshToken = UUID.randomUUID().toString();
        Hmac hmac = new Hmac(refreshToken);
        refreshTokens.put(hmac, new RefreshState(authenticationInfo, session));
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
        RefreshState state = refreshTokens.get(hmac);
        if (state != null) { // Token valid, generate new token (once only)
            String nextToken = generateRefreshToken(state.authenticationInfo, state.userSession);
            try {
                renewSession(state.userSession);
            } catch (SessionExpiredException e) {
                throw new UnauthorizedException("Token expired");
            }
            RefreshResult result = new RefreshResult(state.authenticationInfo, nextToken);
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

    private void renewSession(UserSession userSession) throws SessionExpiredException {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        SessionManager sessionManager = securityStore.getSessionManager();
        sessionManager.renewSession(userSession.getId());
    }

    public synchronized void revokeRefreshToken(String refreshToken) {
        Hmac hmac = new Hmac(refreshToken);
        refreshTokens.remove(hmac);
        refreshCache.invalidate(hmac);
    }

    private static final class RefreshState {
        final AuthenticationInfo authenticationInfo;
        final UserSession userSession;

        RefreshState(AuthenticationInfo authenticationInfo, UserSession userSession) {
            this.authenticationInfo = authenticationInfo;
            this.userSession = userSession;
        }
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
            if (!(obj instanceof Hmac)) {
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
