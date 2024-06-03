package org.yamcs.security;

public interface SessionListener {

    void onCreated(UserSession session);

    void onExpired(UserSession session);

    void onInvalidated(UserSession session);
}
