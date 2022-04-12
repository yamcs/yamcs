package org.yamcs.security;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;

/**
 * Implementation-agnostic session store. Sessions have a limited lifespan, but can be renewed before expiring.
 * <p>
 * In a future iteration, UserSession could be split into UserSession and ClientSession for covering implementations
 * that support SSO across multiple clients (e.g. OIDC).
 */
public class SessionManager {

    protected static final Log log = new Log(SessionManager.class);
    private static final SecureRandom RG = new SecureRandom();

    /**
     * Time before a session is considered to be expired.
     * 
     * In terms of OAuth this corresponds to the lifetime of a refresh token.
     */
    private static final long SESSION_IDLE = 30 * 60 * 1000L; // 30 minutes

    private ConcurrentMap<String, UserSession> sessions = new ConcurrentHashMap<>();

    public SessionManager() {
        YamcsServer yamcs = YamcsServer.getServer();
        yamcs.getThreadPoolExecutor().scheduleWithFixedDelay(this::purgeExpiredSessions, 10, 10, SECONDS);
    }

    public UserSession createSession(String login, String ipAddress, String hostname) {
        String sessionId = generateSessionId();
        UserSession session = new UserSession(sessionId, login, ipAddress, hostname, SESSION_IDLE);
        sessions.put(sessionId, session);
        return session;
    }

    public UserSession getSession(String id) {
        return sessions.get(id);
    }

    public Collection<UserSession> getSessions() {
        return sessions.values();
    }

    public void renewSession(String id) throws SessionExpiredException {
        UserSession session = sessions.get(id);
        if (session == null) {
            throw new SessionExpiredException();
        }
        session.touch();
    }

    private String generateSessionId() {
        // Generate something that is url-safe
        byte[] bytes = new byte[10];
        RG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void purgeExpiredSessions() {
        Set<UserSession> toExpire = sessions.values().stream()
                .filter(session -> session.isExpired())
                .collect(Collectors.toSet());
        for (UserSession expiredSession : toExpire) {
            log.info("Session expired: {}", expiredSession);
            sessions.remove(expiredSession.getId());
        }
    }
}
