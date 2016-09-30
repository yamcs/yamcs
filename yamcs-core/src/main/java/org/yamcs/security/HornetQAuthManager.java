package org.yamcs.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.artemis.YamcsSession;

/**
 * Authenticates and authorises ActiveMQ sessions from configured realm.
 * 
 * See {@link Privilege} for information on authentication, authorisation and caching
 * 
 * A default user is used for anonymous (non-authenticated) connections.
 * Specify a default user (and associated roles) in hornetq-users.xml. If no
 * default user is configured, anonymous connections will be rejected.
 * 
 * @author atu
 *
 */
public class HornetQAuthManager implements ActiveMQSecurityManager {
    static Logger log = LoggerFactory.getLogger(HornetQAuthManager.class);
    private Map<String, ArrayList<String>> configuredUserCache = new HashMap<>();
    /** If null (default), anonymous connections will be rejected. */
    private String defaultUser = null;

    /**
     * Determines if no restrictions should be applied to the specified user,
     * which can mean either that privileges are disabled (everyone has all
     * privilieges), or that the user is an in-process user.
     * 
     * Internal messaging user (in-vm user) is the only current super-user.
     * Null username or password always returns false if privileges enabled.
     * 
     * @param username
     * @param password
     * @return True if configured to not use privileges (see {@link Privilege})
     * 	or if username and password match a super user.
     */
    public boolean isSuperUser( String username, String password ) {
        if( !Privilege.usePrivileges ) {
            return true;
        }
        // Anonymous is never super user when using Privileges
        if( username == null || password == null ) {
            return false;
        }
        // Currently only server processes can be super-user
        return username.equals( YamcsSession.hornetqInvmUser ) && password.equals( YamcsSession.hornetqInvmPass );
    }
/*

    @Override
    public void addRole(String username, String role) {
        // Allow setting a default user from config, used for anonymous connections,
        if( username != null && username == defaultUser ) {
            if( configuredUserCache.containsKey( defaultUser ) ) {
                configuredUserCache.get(defaultUser).add( role );
                log.debug( "Default user '{}' given role '{}'.",username, role );
            }
        } else {
            log.debug( "addRole('{}','{}') called but will perform no function",username, role );
        }
    }

    @Override
    public void setDefaultUser(String username) {
        // Allow setting a default user from config, used for anonymous connections,
        if( defaultUser != null ) {
            configuredUserCache.remove( defaultUser );
        }
        defaultUser = username;
        configuredUserCache.put(defaultUser, new ArrayList<String>());
        log.info( "Default user (used for anonymous connections) set to '{}'",username );
        log.warn( "Anonymous connections allowed, will be treated as user '{}'", username );
    }
*/
    @Override
    public boolean validateUser(String username, String password) {
        // Allow all? Looks for invm processes, or if privileges disabled.
        if( isSuperUser( username, password ) ) {
            return true;
        }

        // Anonymous user taken as default user...
        if( username == null ) {
            // If no default user, anonymous connections are not allowed.
            return ( defaultUser != null );
        }

        if( ! Privilege.getInstance().authenticates(new UsernamePasswordToken(username, password))){

            //if( ! ActiveMQAuthPrivilege.authenticated(username, password) ) {
            return false;
        }
        log.info("User '{}' authenticated with {}", username, Privilege.getRealmName());
        return true;
    }

    /**
     * @param configuredRoles - Set of roles configured for the component being accessed.
     * @param checkType - Permission being sought.
     */
    @Override
    public boolean validateUserAndRole(String username, String password, Set<Role> configuredRoles, CheckType checkType) {
        // Allow all? Looks for invm processes, or if privileges disabled.
        if( isSuperUser( username, password ) ) {
            return true;
        }

        // Anonymous user taken as default user if set
        if( username == null ) {
            if( defaultUser == null ) {
                return false;
            }
            // Default user is only in config, not in realm, so check perms now
            username = defaultUser;
            for( Role configuredRole : configuredRoles ) {
                if( configuredUserCache.get( defaultUser ).contains( configuredRole.getName() ) && checkType.hasRole( configuredRole ) ) {
                    return true;
                }
            }
        }

        // If required roles not configured, warn as configuration error
        if( configuredRoles == null ) {
            log.warn( "No roles configured, cannot validate '{}' for check '{}'", username, checkType );
            return false;
        }

        // Use configuration to set security-invalidation-interval and always
        // check authentication in this method.
        if( ! validateUser( username, password ) ) {
            return false;
        }

        Privilege p = Privilege.getInstance();
        UsernamePasswordToken userToken = new UsernamePasswordToken(username, password);
        for( Role configuredRole : configuredRoles ) {
            if( p.hasRole(userToken, configuredRole.getName() ) && checkType.hasRole( configuredRole ) ) {
                return true;
            }
        }

        log.trace( "User '{}' does not have any of '{}' for '{}'", username, configuredRoles, checkType );
        return false;
    }
}
