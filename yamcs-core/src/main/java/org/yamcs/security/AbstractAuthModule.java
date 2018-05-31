package org.yamcs.security;

/**
 * Implements the authorisation part of the auth module based on the User class.
 * @author nm
 *
 */
public abstract class AbstractAuthModule implements AuthModule {
    @Override
    public String[] getRoles(final AuthenticationToken authenticationToken) {
        // Load user and read roles from result
        User user = getUser(authenticationToken);
        if (user == null) {
            return null;
        }
        return user.getRoles();
    }
    

    /**
     *
     * @param type
     * @param privilege
     *            a opsname of tc, tm parameter or tm packet
     * @return true if the privilege is known and the current user has it.
     */
    @Override
    public boolean hasPrivilege(final AuthenticationToken authenticationToken, PrivilegeType type, String privilege) {
        User user = getUser(authenticationToken);
        if (user == null) {
            return false;
        }
        return user.hasPrivilege(type, privilege);
    }

    @Override
    public boolean hasRole(final AuthenticationToken authenticationToken, String role) {
        // Load user and read role from result
        User user = getUser(authenticationToken);
        if (user == null) {
            return false;
        }
        return user.hasRole(role);
    }

}
