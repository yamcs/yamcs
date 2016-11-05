package org.yamcs.security;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.usoctools.XtceUtil;
import org.yamcs.xtce.MdbMappings;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;


/**
 * Implements privileges loaded and short-term cached from realm.
 *
 * Extending classes provide concrete method to identify the user, which this
 * class then uses to load privileges. This class also provides methods to
 * extract a username from a supplied certificate.
 *
 * Requests are usually made in short bursts, so privileges loaded from realm
 * are cached for a short period to reduce load on the realm service and retain
 * responsiveness.
 *
 * @author nm
 */
public class Privilege {

    public enum SystemPrivilege {
        MayControlProcessor,
        MayModifyCommandHistory,
        MayControlCommandQueue,
        MayCommandPayload,
        MayGetMissionDatabase,
        MayControlArchiving,
        MayControlServices
    }

    public static boolean usePrivileges = true;
    private static String defaultUser; // Only if !usePrivileges. Could eventually replace usePrivileges i guess

    private static Realm realm;
    private static String realmName;

    static final Hashtable<String, String> contextEnv = new Hashtable<>();

    public static int maxNoSessions;
    public static Privilege instance;
    // time to cache a user entry
    static final int PRIV_CACHE_TIME = 30*1000;
    // time to cache a certificate to username mapping
    static private final ConcurrentHashMap<AuthenticationToken, Future<User>> cache = new ConcurrentHashMap<>();
    static Logger log = LoggerFactory.getLogger(Privilege.class);

    public enum Type {
        SYSTEM, TC, TM_PACKET, TM_PARAMETER, TM_PARAMETER_SET
    }

    /**
     * Load configuration once only.
     */
    static {
        try {
            YConfiguration conf=YConfiguration.getConfiguration("privileges");
            maxNoSessions=conf.getInt("maxNoSessions");
            usePrivileges=conf.getBoolean("enabled");

            if(usePrivileges) {
                String realmClass = conf.getString("realm");
                realm = loadRealm(realmClass);
            } else {
                // Intended migration path is that this could replace 'privileges=false', but the interaction with
                // ActiveMQAuthManager is still unclear to me. Looks like a dupe. (fdi)
                if (!conf.containsKey("defaultUser")) {
                    throw new ConfigurationException("'defaultUser' must be specified when privileges are not enabled. For example 'admin', 'anonymous' or 'guest'");
                }
                String defaultUserString = conf.getString("defaultUser");
                if (defaultUserString.isEmpty() || defaultUserString.contains(":")) {
                    throw new ConfigurationException("Invalid name '" + defaultUserString + "' for default user");
                }
                defaultUser = defaultUserString;
            }
        } catch (ConfigurationException e) {
            throw new ConfigurationException("Failed to load 'privileges' configuration", e);
        }
        if(Privilege.usePrivileges) {
            log.info("Privileges enabled, authenticating and authorising from "+realmName);
        } else {
            log.warn("Privileges disabled, all connections are allowed and have full permissions");
        }
    }

    private static Realm loadRealm(String realmClass) throws ConfigurationException {
        // load the specified class;
        Realm realm;
        try {
            realm = (Realm) Realm.class.getClassLoader()
                    .loadClass(realmClass).newInstance();
            realmName = realm.getClass().getSimpleName();
        } catch (Exception e) {
            throw new ConfigurationException("Unable to load the realm class: " + realmClass, e);
        }
        return realm;
    }

    /**
     * loads the configuration of the privilege. If privileges.enabled is not
     * set to false in the privileges.properties, then load the privileges from
     * the LDAP.
     * 
     * @throws ConfigurationException
     *             when the privileges.enabled is not set to false and the ldap
     *             parammeters are not present
     */
    protected Privilege() throws ConfigurationException {
    }

    public static synchronized Privilege getInstance() {
        if (instance == null)
            instance = new Privilege() {};
            
        return instance;
    }

    /**
     * 
     * @return true if privileges are enabled
     */
    public boolean isEnabled() {
        return usePrivileges;
    }



    public boolean authenticates(AuthenticationToken authenticationToken)
    {
        // Load user and check authentication result
        User user = getUser(authenticationToken);
        if(user == null) return false;
        return user.isAuthenticated();
    }


    /**
     *
     * @return the roles of the calling user
     */
    public String[] getRoles(final AuthenticationToken authenticationToken) {
        if(!usePrivileges) return null;

        // Load user and read roles from result
        User user = getUser(authenticationToken);
        if(user == null) return null;
        return user.getRoles();
    }

    /**
     * Convenience method, check if user has specific role.
     * 
     * @param role Title or full dn.
     * @return True if usePrivileges enabled and user has role, false otherwise.
     */
    public boolean hasRole(final AuthenticationToken authenticationToken, String role ) {
        if(!usePrivileges) return false;
        if(authenticationToken == null || authenticationToken.getPrincipal() == null) return false;
        if (isSystemToken(authenticationToken)) return true;

        // Load user and read role from result
        User user = getUser(authenticationToken);
        if(user == null) return false;
        return user.hasRole(role);
    }

    private boolean isSystemToken(final AuthenticationToken authenticationToken) {
        return authenticationToken.getPrincipal().equals(YamcsSession.hornetqInvmUser) || (authenticationToken instanceof SystemToken); 
    }

    /**
     *
     * @param type
     * @param privilege
     *            a opsname of tc, tm parameter or tm packet
     * @return true if the privilege is known and the current user has it.
     */
    public boolean hasPrivilege(final AuthenticationToken authenticationToken, Type type, String privilege) {
        if (!usePrivileges)	return true;
        if(authenticationToken == null || authenticationToken.getPrincipal() == null) return false;
        
        if (isSystemToken(authenticationToken)) return true;

        User user = getUser(authenticationToken);
        if(user == null) return false;
        return user.hasPrivilege(type, privilege);
    }

    public boolean hasPrivilege(final AuthenticationToken authenticationToken, Type type, SystemPrivilege privilege) {
        if (!usePrivileges)     return true;
        if(authenticationToken == null || authenticationToken.getPrincipal() == null) return false;
        
        if (isSystemToken(authenticationToken)) return true;

        User user = getUser(authenticationToken);
        if(user == null) return false;
        return user.hasPrivilege(type, privilege.toString());
    }

    public static String getRealmName() {
        return realmName;
    }
    
    /**
     * Returns the default user if this server is unsecured. Returns null in all other cases.
     */
    public static String getDefaultUser() {
        return defaultUser;
    }

    /**
     * Get packet names this user has appropriate privileges for.
     *
     * @param yamcsInstance Used to get MDB.
     * @param namespace If null defaults to "MDB:OPS Name"
     * @return A collection of TM packet names in the specified namespace for
     * which the user has privileges.
     * @throws ConfigurationException
     */
    public Collection<String> getTmPacketNames(String yamcsInstance, final AuthenticationToken authenticationToken, String namespace) throws ConfigurationException {
        if( namespace == null ) {
            namespace = MdbMappings.MDB_OPSNAME;
        }
        Collection<String> tl=XtceUtil.getInstance(XtceDbFactory.getInstance(yamcsInstance)).getTmPacketNames( namespace );
        ArrayList<String> l=new ArrayList<String>();
        for(String name:tl) {
            if(!hasPrivilege(authenticationToken, Privilege.Type.TM_PACKET, name)) continue;
            l.add(name);
        }
        return l;
    }

    /**
     * Get parameter names this user has appropriate privileges for.
     *
     * @param yamcsInstance Used to get MDB.
     * @param namespace If null defaults to "MDB:OPS Name"
     * @return A collection of TM parameter names in the specified namespace for
     * which the user has privileges.
     * @throws ConfigurationException
     */
    public Collection<String> getTmParameterNames(String yamcsInstance, final AuthenticationToken authenticationToken, String namespace) throws ConfigurationException {
        if( namespace == null ) {
            namespace = MdbMappings.MDB_OPSNAME;
        }
        XtceDb xtcedb = XtceDbFactory.getInstance(yamcsInstance);
        ArrayList<String> l=new ArrayList<String>();
        for(String name: xtcedb.getParameterNames() ) {
            if(!hasPrivilege(authenticationToken, Privilege.Type.TM_PARAMETER, name)) {
                log.trace( "User '{}' does not have privilege '{}' for parameter '{}'", authenticationToken, Privilege.Type.TM_PARAMETER, name );
                continue;
            }
            l.add( xtcedb.getParameter( name ).getAlias( namespace ) );
        }
        return l;
    }


    public int getMaxNoSessions()
    {
        return maxNoSessions;
    }



    public User getUser(final AuthenticationToken authenticationToken)  {
        while (true) {
            if(authenticationToken == null)
                return null;
            Future<User> f = cache.get(authenticationToken);
            if (f == null) {
                Callable<User> eval = new Callable<User>() {
                    @Override
                    public User call() {
                        try {
                            // check the realm support the type of provided token
                            if (!realm.supports(authenticationToken)) {
                                log.error("Realm " + Privilege.realmName + " does not support authentication token of type"
                                        + authenticationToken.getClass());
                                return null;
                            }
                            return realm.loadUser(authenticationToken);
                        }
                        catch (Exception e)
                        {
                            log.error("Unable to load user from realm " + realmName, e);
                            return new User(authenticationToken);
                        }
                    }
                };
                FutureTask<User> ft = new FutureTask<User>(eval);
                f = cache.putIfAbsent(authenticationToken, ft);
                if (f == null) {
                    f = ft;
                    ft.run();
                }
            }
            try {
                User u = f.get();
                if ((System.currentTimeMillis() - u.lastUpdated) < PRIV_CACHE_TIME)
                    return u;
                cache.remove(authenticationToken, f); // too old
            } catch (CancellationException e) {
                cache.remove(authenticationToken, f);
            } catch (ExecutionException e) {
                cache.remove(authenticationToken,f); //we don't cache exceptions
                if (e.getCause() instanceof RuntimeException)
                    throw (RuntimeException) e.getCause();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            catch (Exception e)
            {
                log.error("Unable to load user");
                return null;
            }
        }

    }


    /**
     * @param args
     * @throws InterruptedException
     * @throws ConfigurationException
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.err.println("usage: print-priv.sh username | -f certificate.pem");
            System.exit(-1);
        }
        Privilege priv = Privilege.getInstance();
        int n = 1;
        for (int i = 0; i < n; i++) {
            long start = System.currentTimeMillis();
            try {

                AuthenticationToken authenticationToken = null;

                if (args[0].equals("-f")) {
                    java.io.FileInputStream fis = new java.io.FileInputStream(args[1]);
                    java.io.BufferedInputStream bis = new java.io.BufferedInputStream(fis);

                    java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");

                    while (bis.available() > 0) {
                        X509Certificate cert = (X509Certificate) cf.generateCertificate(bis);
                        System.out.println("\n*******For certificate "+ cert.getIssuerX500Principal() + ":");
                        authenticationToken = new CertificateToken(cert);
                        // checking only the certificate username, not against the certificate binary data
                    }
                } else {
                    authenticationToken = new UsernamePasswordToken(args[0], "");
                }

                System.out.println(priv.getUser(authenticationToken));
            } catch (Exception e) {
                System.err.println("got Exception: "+e);
            }
            Thread.sleep(1000);

            long end = System.currentTimeMillis();
            System.out.println("took " + (end - start) + " milisec");
        }
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
