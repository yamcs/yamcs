package org.yamcs.security;

import java.util.concurrent.CompletableFuture;

import org.yamcs.security.Privilege.Type;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

/**
 * Interface implemented by the Authentication and Authorization modules.
 * 
 * Each user has a list of privileges and a list of roles. The roles are used for the 
 * Commanding system to decide in which queue will be put the commands sent by the user.  
 * 
 * Usually the roles are associated to privileges but this class makes no assumption about that.
 * 
 * 
 * The {@link #authenticateHttp(ChannelHandlerContext, HttpRequest)} is called at the reception of the http request
 *  and returns an authentication token that is used later on to check for privileges and roles.
 *  
 * Note that while the {@link #authenticateHttp(ChannelHandlerContext, HttpRequest)} method is asynchronous, the other methods
 *  are supposed to return fast, so the information about the user privileges has to be cached.  
 * 
 *  For the short lived REST requests the privilege check follows in quick succesion the authenticate call. For the websocket, however
 *  the check privilege it can come a long time after the authenticate so in case the token expires, a preemptive renewal strategy
 *  has to be implemented. 
 * 
 * @author nm
 *
 */
public interface AuthModule {
    /**
     * Authenticate the request and return a CompletableFuture to indicate the completion.
     * 
     * Possibly send already an answer on the ctx. 
     * 
     * @param ctx
     * @param req
     * @return an AuthenticationToken that will be passed later in the checkPrivileges methods
     * 
     */
    CompletableFuture<AuthenticationToken> authenticateHttp(ChannelHandlerContext ctx, HttpRequest req);
    

    /**
     * Get the list of roles of the user. 
     *
     * @param authenticationToken 
     * @return the roles of the calling user
     * @throws InvalidAuthenticationToken thrown in case the authentication token is not (anymore) valid
     */
    public String[] getRoles(final AuthenticationToken authenticationToken) throws InvalidAuthenticationToken;
    
    /**
     * 
     * @param authenticationToken
     * @param role
     * 
     * @throws InvalidAuthenticationToken thrown in case the authentication token is not (anymore) valid
     * @return true if the user identified by the token is part of the requested role
     */
    public boolean hasRole(final AuthenticationToken authenticationToken, String role) throws InvalidAuthenticationToken;
    
    /**
     * 
     * @param authenticationToken
     * @param type
     * @param privilege
     * @throws InvalidAuthenticationToken thrown in case the authentication token is not (anymore) valid
     * @return true if the user identified by the token has the privilege
     */
    public boolean hasPrivilege(final AuthenticationToken authenticationToken, Type type, String privilege) throws InvalidAuthenticationToken;

    /**
     * returns the user authenticated by the token
     * 
     * @param authToken
     * @return the authenticated user
     */
    User getUser(AuthenticationToken authToken);
    
}
