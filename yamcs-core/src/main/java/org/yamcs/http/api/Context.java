package org.yamcs.http.api;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import org.yamcs.api.Observer;
import org.yamcs.security.User;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

/**
 * Request context used in RPC-style endpoints.
 */
public class Context {

    /**
     * Unique id for this call.
     */
    public final int id;

    /**
     * The Netty request context for an RPC call. In general RPC implementation should avoid using this object. It is
     * exposed only because we need it for some HTTP-specific functionalities that are not covered by our RPC
     * implementation (e.g. http chunking)
     */
    public final ChannelHandlerContext nettyContext;

    /**
     * The Netty HTTP request for an RPC call. In general RPC implementations should avoid using this object. It is
     * exposed only because we need it for some HTTP-specific functionalities that are not covered by our RPC
     * implementation (e.g. multipart uploading)
     */
    public final FullHttpRequest nettyRequest;

    /**
     * The request user.
     */
    public final User user;

    // Not exposed. The goal is to gradually reduce features from RestRequest in favour of Context
    private RestRequest restRequest;

    /**
     * A future that covers the full API call.
     * <p>
     * API implementations should use the passed {@link Observer} instead of this future.
     */
    CompletableFuture<Void> requestFuture;

    Context(RestRequest restRequest) {
        this.restRequest = restRequest;

        this.id = restRequest.getRequestId();
        this.requestFuture = restRequest.getCompletableFuture();
        this.nettyContext = restRequest.getChannelHandlerContext();
        this.nettyRequest = restRequest.getHttpRequest();
        this.user = restRequest.getUser();
    }

    public void addTransferredSize(int byteCount) {
        restRequest.addTransferredSize(byteCount);
    }

    public void reportStatusCode(int statusCode) {
        // TODO It should be possible to refactor this such that
        // the HTTP status code becomes the result of the requestFuture.
        restRequest.reportStatusCode(statusCode);
    }

    public String getClientAddress() {
        InetSocketAddress address = (InetSocketAddress) nettyContext.channel().remoteAddress();
        return address.getAddress().getHostAddress();
    }
}
