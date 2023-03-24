package org.yamcs.http.api;

import java.time.Instant;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.Context;
import org.yamcs.protobuf.AbstractSessionsApi;
import org.yamcs.protobuf.ListSessionsResponse;
import org.yamcs.protobuf.SessionInfo;
import org.yamcs.security.SecurityStore;
import org.yamcs.security.SessionManager;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.UserSession;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;

public class SessionsApi extends AbstractSessionsApi<Context> {

    @Override
    public void listSessions(Context ctx, Empty request, Observer<ListSessionsResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAccess);

        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        SessionManager sessionManager = securityStore.getSessionManager();

        ListSessionsResponse.Builder responseb = ListSessionsResponse.newBuilder();
        sessionManager.getSessions().stream()
                .forEach(session -> responseb.addSessions(toSession(session)));
        observer.complete(responseb.build());
    }

    private static SessionInfo toSession(UserSession session) {
        SessionInfo.Builder proto = SessionInfo.newBuilder()
                .setId(session.getId())
                .setUsername(session.getLogin())
                .setIpAddress(session.getIpAddress())
                .setHostname(session.getHostname())
                .setStartTime(toTimestamp(session.getStartTime()))
                .setLastAccessTime(toTimestamp(session.getLastAccessTime()))
                .setExpirationTime(toTimestamp(session.getExpirationTime()))
                .addAllClients(session.getClients());
        return proto.build();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamps.fromMillis(instant.toEpochMilli());
    }
}
