package org.yamcs.client;

import io.netty.handler.codec.http.HttpRequest;

public interface Credentials {

    boolean isExpired();

    void modifyRequest(HttpRequest request);
}
