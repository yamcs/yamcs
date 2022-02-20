package org.yamcs.http.api;

import java.time.Instant;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.Context;
import org.yamcs.http.ForbiddenException;
import org.yamcs.protobuf.AbstractSessionsApi;
import org.yamcs.protobuf.ListSessionsResponse;
import org.yamcs.protobuf.SessionInfo;
import org.yamcs.security.SecurityStore;
import org.yamcs.security.SessionManager;
import org.yamcs.security.UserSession;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;

public class SessionsApi extends AbstractSessionsApi<Context> {

    @Override
    public void listSessions(Context ctx, Empty request, Observer<ListSessionsResponse> observer) {
        if (!ctx.user.isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }

        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        SessionManager sessionManager = securityStore.getSessionManager();

        Instant now = Instant.now(); // Use same relto for all expiration times
        ListSessionsResponse.Builder responseb = ListSessionsResponse.newBuilder();
        sessionManager.getSessions().stream()
                .forEach(session -> responseb.addSessions(toSession(session, sessionManager, now)));
        observer.complete(responseb.build());
    }

    private SessionInfo toSession(UserSession session, SessionManager sessionManager, Instant now) {
        long lifespan = sessionManager.getSessionIdleTime();
        SessionInfo.Builder proto = SessionInfo.newBuilder()
                .setId(session.getId())
                .setUsername(session.getLogin())
                .setIpAddress(session.getIpAddress())
                .setHostname(session.getHostname())
                .setStartTime(toTimestamp(session.getStartTime()))
                .setLastAccessTime(toTimestamp(session.getLastAccessTime()))
                .setExpirationTime(toTimestamp(session.getExpirationTime(lifespan)));
        return proto.build();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamps.fromMillis(instant.toEpochMilli());
    }
}
