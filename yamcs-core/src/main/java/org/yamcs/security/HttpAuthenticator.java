package org.yamcs.security;

import java.util.concurrent.CompletableFuture;

import org.yamcs.web.BadRequestException;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

public interface HttpAuthenticator {
    /**
     * Authenticate the request and return a CompletableFuture to indicate the completion.
     * 
     * Possibly send already an answer on the ctx. 
     * 
     * @param ctx
     * @param req
     * @return
     */
    CompletableFuture<AuthenticationToken> authenticate(ChannelHandlerContext ctx, HttpRequest req) throws BadRequestException, AuthorizationPendingException;
}
