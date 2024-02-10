package org.yamcs.http.api;

import java.time.Instant;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.Context;
import org.yamcs.http.HttpHandler;
import org.yamcs.http.HttpRequestHandler;
import org.yamcs.http.HttpServer;
import org.yamcs.http.UnauthorizedException;
import org.yamcs.protobuf.AbstractSessionsApi;
import org.yamcs.protobuf.ListSessionsResponse;
import org.yamcs.protobuf.SessionEventInfo;
import org.yamcs.protobuf.SessionInfo;
import org.yamcs.security.AuthenticationInfo;
import org.yamcs.security.SecurityStore;
import org.yamcs.security.SessionListener;
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

    @Override
    public void subscribeSession(Context ctx, Empty request, Observer<SessionEventInfo> observer) {
        // The Context does not currently provide easy access to the session id,
        // so this here is somewhat unelegant.
        AuthenticationInfo authenticationInfo = null;
        var httpRequest = ctx.nettyContext.channel().attr(HttpRequestHandler.CTX_HTTP_REQUEST).get();

        if (httpRequest != null) {
            var accessToken = HttpHandler.getAccessTokenFromCookie(httpRequest);
            if (accessToken != null) {
                var httpServer = YamcsServer.getServer().getGlobalService(HttpServer.class);
                var tokenStore = httpServer.getTokenStore();
                try {
                    authenticationInfo = tokenStore.verifyAccessToken(accessToken);
                } catch (UnauthorizedException e) {
                    // Ignore
                }
            }
        }

        if (authenticationInfo == null) {
            return;
        }

        var fAuthenticationInfo = authenticationInfo;
        var securityStore = YamcsServer.getServer().getSecurityStore();
        var sessionManager = securityStore.getSessionManager();
        var sessionListener = new SessionListener() {
            @Override
            public void onCreated(UserSession session) {
            }

            @Override
            public void onInvalidated(UserSession session) {
                if (fAuthenticationInfo.equals(session.getAuthenticationInfo())) {
                    observer.next(SessionEventInfo.newBuilder().setEndReason("Session invalidated").build());
                }
            }

            @Override
            public void onExpired(UserSession session) {
                if (fAuthenticationInfo.equals(session.getAuthenticationInfo())) {
                    observer.next(SessionEventInfo.newBuilder().setEndReason("Session expired").build());
                }
            }
        };
        observer.setCancelHandler(() -> sessionManager.removeSessionListener(sessionListener));
        sessionManager.addSessionListener(sessionListener);
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
