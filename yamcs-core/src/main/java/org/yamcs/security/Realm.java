package org.yamcs.security;

/**
 * Created by msc on 05/05/15.
 */
public interface Realm {

    /**
     * supports
     * @param authenticationToken
     * @return true if the authenticationToken is supported by this realm, false otherwise
     */
    public boolean supports(AuthenticationToken authenticationToken);

    /**
     *  authenticates, check that the user authenticates properly
     * @param authenticationToken
     * @return
     */
    public boolean authenticates(AuthenticationToken authenticationToken);

    /**
     * loadUser, load the roles and privileges of the user
     * @param authenticationToken
     * @return User with roles and priviledges loaded
     */
    public User loadUser(AuthenticationToken authenticationToken);

}
