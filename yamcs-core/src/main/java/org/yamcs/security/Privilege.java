package org.yamcs.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.MdbMappings;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

/**
 * Implements privileges loaded and short-term cached from realm.
 *
 * Extending classes provide concrete method to identify the user, which this class then uses to load privileges. This
 * class also provides methods to extract a username from a supplied certificate.
 *
 * Requests are usually made in short bursts, so privileges loaded from realm are cached for a short period to reduce
 * load on the realm service and retain responsiveness.
 *
 * @author nm
 */
public class Privilege {

    private static final Logger log = LoggerFactory.getLogger(Privilege.class);
    private static Privilege instance;

    private String authModuleName;
    private boolean usePrivileges;
    private String defaultUser; // Only if !usePrivileges

    private AuthModule authModule;

    private int maxNoSessions;

    private Privilege() {
        defaultUser = "admin";
        maxNoSessions = 10;
        usePrivileges = false;
        if (YConfiguration.isDefined("privileges")) {
            try {
                YConfiguration conf = YConfiguration.getConfiguration("privileges");
                if (conf.containsKey("maxNoSessions")) {
                    maxNoSessions = conf.getInt("maxNoSessions");
                }
                usePrivileges = conf.getBoolean("enabled");

                if (usePrivileges) {
                    authModule = YObjectLoader.loadObject(conf.getMap("authModule"));
                    authModuleName = authModule.getClass().getName();
                } else {
                    if (conf.containsKey("defaultUser")) {
                        String defaultUserString = conf.getString("defaultUser");
                        if (defaultUserString.isEmpty() || defaultUserString.contains(":")) {
                            throw new ConfigurationException(
                                    "Invalid name '" + defaultUserString + "' for default user");
                        }
                        defaultUser = defaultUserString;
                    }
                }
            } catch (IOException | ConfigurationException e) {
                throw new ConfigurationException("Failed to load 'privileges' configuration", e);
            }
        }

        if (usePrivileges) {
            log.info("Privileges enabled, authenticating and authorising by module {}", authModule);
        } else {
            log.warn("Privileges disabled, all connections are allowed and have full permissions");
        }
    }

    public String[] getRoles(final AuthenticationToken authenticationToken) throws InvalidAuthenticationToken {
        if (!usePrivileges) {
            return null;
        }

        return authModule.getRoles(authenticationToken);
    }

    public static synchronized Privilege getInstance() {
        if (instance == null) {
            instance = new Privilege();
        }
        return instance;
    }

    /**
     *
     * @return true if privileges are enabled
     */
    public boolean isEnabled() {
        return usePrivileges;
    }

    /**
     * Convenience method, check if user has specific role.
     * 
     * @param authenticationToken
     *
     * @param role
     *            Title or full dn.
     * @return True if usePrivileges enabled and user has role, false otherwise.
     * @throws InvalidAuthenticationToken
     */
    public boolean hasRole(final AuthenticationToken authenticationToken, String role)
            throws InvalidAuthenticationToken {
        if (!usePrivileges) {
            return false;
        }

        if (authenticationToken == null || authenticationToken.getPrincipal() == null) {
            return false;
        }
        if (isSystemToken(authenticationToken)) {
            return true;
        }

        return authModule.hasRole(authenticationToken, role);
    }

    private boolean isSystemToken(final AuthenticationToken authenticationToken) {
        return authenticationToken instanceof SystemToken;
    }

    /**
     *
     * @param authenticationToken
     * @param type
     * @param privilege
     *            a opsname of tc, tm parameter or tm packet
     * @return true if the privilege is known and the current user has it.
     * @throws InvalidAuthenticationToken
     */
    public boolean hasPrivilege(final AuthenticationToken authenticationToken, PrivilegeType type, String privilege)
            throws InvalidAuthenticationToken {
        if (!usePrivileges) {
            return true;
        }

        if (authenticationToken == null || authenticationToken.getPrincipal() == null) {
            return false;
        }

        if (isSystemToken(authenticationToken)) {
            return true;
        }

        return authModule.hasPrivilege(authenticationToken, type, privilege);
    }

    /**
     * Like the method above but instead of throwing InvalidAuthenticationToken, it returns false in case the token is
     * not valid.
     * 
     * To be used to avoid dealing with the exception in case we know that the token must be valid (i.e. immediately
     * after a authenticate)
     * 
     * @param authenticationToken
     * @param type
     * @param privilege
     * @return true if the user has the system privilege
     */
    public boolean hasPrivilege1(final AuthenticationToken authenticationToken, PrivilegeType type, String privilege) {
        if (!usePrivileges) {
            return true;
        }
        try {
            return hasPrivilege(authenticationToken, type, privilege);
        } catch (InvalidAuthenticationToken e) {
            return false;
        }
    }

    /**
     * 
     * @param authenticationToken
     * @param privilege
     * @return true if the user has the system privilege
     * @throws InvalidAuthenticationToken
     */
    public boolean hasPrivilege(final AuthenticationToken authenticationToken, SystemPrivilege privilege)
            throws InvalidAuthenticationToken {
        if (!usePrivileges) {
            return true;
        }
        if (authenticationToken == null || authenticationToken.getPrincipal() == null) {
            return false;
        }

        if (isSystemToken(authenticationToken)) {
            return true;
        }

        return hasPrivilege(authenticationToken, PrivilegeType.SYSTEM, privilege.name());
    }

    /**
     * Like above but instead of throwing InvalidAuthenticationToken, it returns false if the token is not valid.
     * 
     * @param authToken
     * @param sysPrivilege
     * @return true if the user has the system privilege
     */
    public boolean hasPrivilege1(AuthenticationToken authToken, SystemPrivilege sysPrivilege) {
        try {
            return hasPrivilege(authToken, sysPrivilege);
        } catch (InvalidAuthenticationToken e) {
            return false;
        }
    }

    public String getAuthModuleName() {
        return authModuleName;
    }

    /**
     * Returns the default user if this server is unsecured. Returns null in all other cases.
     * 
     * @return default username
     */
    public String getDefaultUser() {
        return defaultUser;
    }

    /**
     * Get packet names this user has appropriate privileges for.
     *
     * @param yamcsInstance
     *            Used to get MDB.
     * @param authToken
     * @param namespace
     *            If null defaults to "MDB:OPS Name"
     * @return A collection of TM packet names in the specified namespace for which the user has privileges.
     * @throws ConfigurationException
     * @throws InvalidAuthenticationToken
     */
    public Collection<String> getTmPacketNames(String yamcsInstance, final AuthenticationToken authToken,
            String namespace) throws ConfigurationException, InvalidAuthenticationToken {
        if (namespace == null) {
            namespace = MdbMappings.MDB_OPSNAME;
        }
        Collection<String> tl = getTmPacketNames(XtceDbFactory.getInstance(yamcsInstance), namespace);
        ArrayList<String> l = new ArrayList<>();
        for (String name : tl) {
            if (!hasPrivilege(authToken, PrivilegeType.TM_PACKET, name)) {
                continue;
            }
            l.add(name);
        }
        return l;
    }

    /**
     * Get packet names this user has appropriate privileges for.
     *
     * @param yamcsInstance
     *            Used to get MDB.
     * @param authToken
     * @param namespace
     *            If null defaults to "MDB:OPS Name"
     * @return A collection of TM packet names in the specified namespace for which the user has privileges.
     * @throws ConfigurationException
     * @throws InvalidAuthenticationToken
     */
    public Collection<String> getTmPacketNames(String yamcsInstance, final AuthenticationToken authToken)
            throws ConfigurationException {
        XtceDb xtcedb = XtceDbFactory.getInstance(yamcsInstance);
        ArrayList<String> tl = new ArrayList<>();
        for (SequenceContainer sc : xtcedb.getSequenceContainers()) {
            if (hasPrivilege1(authToken, PrivilegeType.TM_PACKET, sc.getQualifiedName())) {
                tl.add(sc.getQualifiedName());
            }
        }
        return tl;
    }

    private Collection<String> getTmPacketNames(XtceDb xtcedb, String namespace) {
        ArrayList<String> pn = new ArrayList<>();
        for (SequenceContainer sc : xtcedb.getSequenceContainers()) {
            String alias = sc.getAlias(namespace);
            if (alias != null) {
                pn.add(alias);
            }
        }
        return pn;
    }

    /**
     * Get parameter names this user has appropriate privileges for.
     *
     * @param yamcsInstance
     *            Used to get MDB.
     * @param authToken
     * @param namespace
     *            If null defaults to "MDB:OPS Name"
     * @return A collection of TM parameter names in the specified namespace for which the user has privileges.
     * @throws ConfigurationException
     * @throws InvalidAuthenticationToken
     */
    public Collection<String> getTmParameterNames(String yamcsInstance, final AuthenticationToken authToken,
            String namespace) throws ConfigurationException, InvalidAuthenticationToken {
        if (namespace == null) {
            namespace = MdbMappings.MDB_OPSNAME;
        }
        XtceDb xtcedb = XtceDbFactory.getInstance(yamcsInstance);
        ArrayList<String> l = new ArrayList<>();
        for (String name : xtcedb.getParameterNames()) {
            if (!hasPrivilege(authToken, PrivilegeType.TM_PARAMETER, name)) {
                log.trace("User '{}' does not have privilege '{}' for parameter '{}'", authToken,
                        PrivilegeType.TM_PARAMETER, name);
                continue;
            }
            l.add(xtcedb.getParameter(name).getAlias(namespace));
        }
        return l;
    }

    public int getMaxNoSessions() {
        return maxNoSessions;
    }

    public String getUsername(AuthenticationToken authToken) {
        if (!usePrivileges) {
            return defaultUser;
        }

        User u = authModule.getUser(authToken);
        if (u == null) {
            return null;
        }

        return u.getPrincipalName();
    }

    public User getUser(AuthenticationToken authToken) {
        if (!usePrivileges) {
            return null;
        }

        return authModule.getUser(authToken);
    }

    public AuthModule getAuthModule() {
        return authModule;
    }

    public CompletableFuture<AuthenticationToken> authenticateHttp(ChannelHandlerContext ctx, HttpRequest req) {
        return authModule.authenticateHttp(ctx, req);
    }
}

class UsernameCache {
    UsernameCache(String un, long time) {
        username = un;
        lastUpdated = time;
    }

    String username;
    long lastUpdated;
}
