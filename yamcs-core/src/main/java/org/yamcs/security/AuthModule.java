package org.yamcs.security;

import java.util.concurrent.CompletableFuture;

import org.yamcs.web.AuthHandler;

/**
 * Interface implemented by the Authentication and Authorization modules.
 * 
 * Each user has a list of privileges and a list of roles. The roles are used for the Commanding system to decide in
 * which queue will be put the commands sent by the user.
 * 
 * Usually the roles are associated to privileges but this class makes no assumption about that.
 * 
 * The AuthModule has to associate to each user an AuthenticationToken - based on this the Yamcs Web will {@link AuthHandler}
 *  generate a JWT token which is passed between the client and the server with each request. 
 *   
 * 
 * @author nm
 *
 */
public interface AuthModule {
    public final static String TYPE_USERPASS = "USERNAME_PASSWORD";
    public final static String TYPE_CODE = "AUTH_CODE";
    /**
     * Create an AuthenticationToken object based on some information received from the user
     * 
     * This operation may involve contacting back the authentication server so it is asynchronous.
     * 
     * @param type - the type of the authObject
     * @param authObject 
     * @return
     */
    CompletableFuture<AuthenticationToken> authenticate(String type, Object authObject);
    
    /**
     * Verify if the token is (still) valid.
     * 
     * @param authenticationToken - token to be verified
     * @return true if the token is valid, false otherwise
     * 
     */
    boolean verifyToken(AuthenticationToken authenticationToken);

    /**
     * Get the list of roles of the user.
     *
     * @param authenticationToken
     * @return the roles of the calling user
     * @throws InvalidAuthenticationToken
     *             thrown in case the authentication token is not (anymore) valid
     */
    public String[] getRoles(final AuthenticationToken authenticationToken) throws InvalidAuthenticationToken;

    /**
     * 
     * @param authenticationToken
     * @param role
     * 
     * @throws InvalidAuthenticationToken
     *             thrown in case the authentication token is not (anymore) valid
     * @return true if the user identified by the token is part of the requested role
     */
    public boolean hasRole(final AuthenticationToken authenticationToken, String role)
            throws InvalidAuthenticationToken;

    /**
     * 
     * @param authenticationToken
     * @param type
     * @param privilege
     * @throws InvalidAuthenticationToken
     *             thrown in case the authentication token is not (anymore) valid
     * @return true if the user identified by the token has the privilege
     */
    public boolean hasPrivilege(final AuthenticationToken authenticationToken, PrivilegeType type, String privilege)
            throws InvalidAuthenticationToken;

    /**
     * returns the user authenticated by the token
     * 
     * @param authToken
     * @return the authenticated user
     */
    User getUser(AuthenticationToken authToken);
}
