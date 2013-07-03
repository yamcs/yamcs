package org.yamcs;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.YamcsSession;
import org.yamcs.usoctools.XtceUtil;
import org.yamcs.xtce.MdbMappings;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;


/**
 * Implements privileges loaded and short-term cached from LDAP.
 *
 * Extending classes provide concrete method to identify the user, which this
 * class then uses to load privileges. This class also provides methods to
 * extract a username from a supplied certificate.
 *
 * Requests are usually made in short bursts, so privileges loaded from LDAP
 * are cached for a short period to reduce load on the LDAP service and retain
 * responsiveness.
 *
 * @author nm
 */
public abstract class Privilege {
	public static boolean usePrivileges = true;

	static final Hashtable<String, String> contextEnv = new Hashtable<String, String>();
	public static String tmParaPrivPath;
	public static String tmPacketPrivPath;
	public static String tcPrivPath;
	public static String systemPrivPath;
	public static String rolePath;
	public static String userPath;
	public static int maxNoSessions;
	public static Privilege instance;
	// time to cache a user entry
	static final int PRIV_CACHE_TIME = 30*1000;
	// time to cache a certificate to username mapping
	static final int CERT2USERNAME_CACHE_TIME = 60 * 1000;
	static private final ConcurrentHashMap<String, Future<User>> cache = new ConcurrentHashMap<String, Future<User>>();
	static private final ConcurrentHashMap<X509Certificate, UsernameCache> certCache = new ConcurrentHashMap<X509Certificate, UsernameCache>();
	static Logger log = LoggerFactory.getLogger("org.yamcs.Privilege");
	
	public enum Type {
		SYSTEM, TC, TM_PACKET, TM_PARAMETER
	};

	/**
	 * Load configuration once only.
	 */
	static {
		try {
			YConfiguration conf=YConfiguration.getConfiguration("privileges");
			maxNoSessions=conf.getInt("maxNoSessions");
			usePrivileges=conf.getBoolean("enabled");
			if(usePrivileges) {
				String host = conf.getString("ldaphost");
				userPath = conf.getString("userPath");
				rolePath = conf.getString("rolePath");
				systemPrivPath = conf.getString("systemPath");
				tmParaPrivPath = conf.getString("tmParameterPath");
				tmPacketPrivPath = conf.getString("tmPacketPath");
				tcPrivPath = conf.getString("tcPath");
				contextEnv.put(Context.INITIAL_CONTEXT_FACTORY,	"com.sun.jndi.ldap.LdapCtxFactory");
				contextEnv.put(Context.PROVIDER_URL, "ldap://" + host);
				contextEnv.put("com.sun.jndi.ldap.connect.pool", "true");
			}
		} catch (ConfigurationException e) {
			log.error("Failed to load 'privileges' configuration: ", e);
			System.exit( -1 );
		}
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
			try {
				instance = new Privilege() {
                    
                    @Override
                    public String getCurrentUser() {
                        // TODO Auto-generated method stub
                        return null;
                    }
                };;;
			} catch (ConfigurationException e) {
				System.err.println("Could not create privileges: " + e);
				System.exit(-1);
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
	 * 
	 * @return the user name of the calling corba
	 */
	public abstract String getCurrentUser();

	private String getCurrentUserDn() {
		String un = getCurrentUser();
		if (un == null)
			return null;
		return "uid=" + un + "," + userPath;
	}

	/**
	 * 
	 * @return the roles of the calling corba user
	 */
	public String[] getRoles() {
		if(!usePrivileges) return null;
		Pattern dn2rolePattern=Pattern.compile("\\w+=([^,]+),.*");
		
		String dn=getCurrentUserDn();
		if(dn==null) return null;
		User u;
		try {
			u = getUser(dn);
			if(u.rolesDn==null) return null;
			String[] roles=new String[u.rolesDn.size()];
			int i=0;
			for(String roledn:u.rolesDn) {
				Matcher m=dn2rolePattern.matcher(roledn);
				if(m.matches()) roles[i]=m.group(1);
				else roles[i]=roledn;
				i++;
			}
			return roles;
		} catch (NamingException e) {
			log.error("NamingException caught when reading the user:", e);
		}
		return null;
		
	}

	/**
	 * Convenience method, check if user has specific role.
	 * 
	 * @param role Title or full dn.
	 * @return True if usePrivileges enabled and user has role, false otherwise.
	 */
	public boolean hasRole( String role ) {
		if(!usePrivileges) return false;
		if( getCurrentUser().equals( YamcsSession.hornetqInvmUser ) ) return true;
		String dn=getCurrentUserDn();
		if(dn==null) return false;
		User u;
		try {
			u = getUser(dn);
			if(u.rolesDn==null) return false;
			String requestedRoleDn = "cn="+role+","+rolePath.replaceAll(" ", "");
			return ( u.rolesDn.contains( requestedRoleDn ) || u.rolesDn.contains( role ) );
		} catch (NamingException e) {
			log.error("NamingException caught when reading the user:", e);
		}
		return false;
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
	public Collection<String> getTmPacketNames(String yamcsInstance, String namespace) throws ConfigurationException {
		if( namespace == null ) {
			namespace = MdbMappings.MDB_OPSNAME;
		}
		Collection<String> tl=XtceUtil.getInstance(XtceDbFactory.getInstance(yamcsInstance)).getTmPacketNames( namespace );
		ArrayList<String> l=new ArrayList<String>();
		for(String name:tl) {
			if(!hasPrivilege(Privilege.Type.TM_PACKET, name)) continue;
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
	public Collection<String> getTmParameterNames(String yamcsInstance, String namespace) throws ConfigurationException {
		if( namespace == null ) {
			namespace = MdbMappings.MDB_OPSNAME;
		}
		XtceDb xtcedb = XtceDbFactory.getInstance(yamcsInstance);
		ArrayList<String> l=new ArrayList<String>();
		for(String name: xtcedb.getParameterNames() ) {
			if(!hasPrivilege(Privilege.Type.TM_PARAMETER, name)) {
				log.trace( "User '{}' does not have privilege '{}' for parameter '{}'", this.getCurrentUser(), Privilege.Type.TM_PARAMETER, name );
				continue;
			}
			l.add( xtcedb.getParameter( name ).getAlias( namespace ) );
		}
		return l;
	}
	
	/**
	 * 
	 * @param type
	 * @param privilege
	 *            a opsname of tc, tm parameter or tm packet
	 * @return true if the privilege is known and the current user has it.
	 */
	public boolean hasPrivilege(Type type, String privilege) {
		if (!usePrivileges)
			return true;
		if( getCurrentUser().equals( YamcsSession.hornetqInvmUser ) )
			return true;

		String dn = getCurrentUserDn();
		if (dn == null)
			return false;
		try {
			User u = getUser(dn);
			Set<String> priv = null;
			switch (type) {
			case TM_PARAMETER:
				priv = u.tmParaPrivileges;
				break;
			case TC:
				priv = u.tcPrivileges;
				break;
			case TM_PACKET:
				priv = u.tmPacketPrivileges;
				break;
			case SYSTEM:
				priv = u.systemPrivileges;
			}
			if (priv == null)
				return false;
			for (String p : priv) {
				if (privilege.matches(p))
					return true;
			}
		} catch (NamingException e) {
			log.warn("got exception when reading user " + dn + " :", e);
			return false;
		}
		return false;
	}

	/**
	 * Checks if a user authenticated with the given X509Certificate (typical a
	 * corba proxy) can assert the identity of another user
	 * 
	 * @param userName
	 * @param assertedIdentity
	 * @return true if the principal is allowed to assert the identity
	 */
	public boolean canAssertIdentity(String userName, String assertedIdentity) {
		if (!usePrivileges)
			return true;
		try {
			String dn = "uid=" + userName + "," + userPath;
			User u = getUser(dn);
			return u.assertedIdentities.contains("uid=" + assertedIdentity+ "," + userPath);
		} catch (NamingException e) {
			log.error("caught NamingException while verifying if " + userName+ " can assert identity of " + assertedIdentity + ":", e);
			return false;
		}
	}

	protected User getUser(final String dn) throws NamingException {
		while (true) {
			Future<User> f = cache.get(dn);
			if (f == null) {
				Callable<User> eval = new Callable<User>() {
					public User call() throws NamingException {
						return loadUserFromLdap(dn);
					}
				};
				FutureTask<User> ft = new FutureTask<User>(eval);
				f = cache.putIfAbsent(dn, ft);
				if (f == null) {
					f = ft;
					ft.run();
				}
			}
			try {
				User u = f.get();
				if ((System.currentTimeMillis() - u.lastUpdated) < PRIV_CACHE_TIME)
					return u;
				cache.remove(dn, f); // too old
			} catch (CancellationException e) {
				cache.remove(dn, f);
			} catch (ExecutionException e) {
				cache.remove(dn,f); //we don't cache exceptions
				if (e.getCause() instanceof RuntimeException)
					throw (RuntimeException) e.getCause();
				if (e.getCause() instanceof NamingException)
					throw (NamingException) e.getCause();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			}
		}

	}

	/**
	 * Loads a user from LDAP together with all the roles and the privileges.
	 * 
	 * @param username
	 * @return
	 * @throws NamingException
	 */
	private User loadUserFromLdap(String dn) throws NamingException {
		User u = new User();
		u.dn = dn;
		u.lastUpdated = System.currentTimeMillis();
		DirContext context = new InitialDirContext(contextEnv);
		try {
			u.assertedIdentities = loadAssertedIdentities(context, dn);
			u.rolesDn = loadRoles(context, dn);
			if (u.rolesDn == null)
				return u;
			u.tmParaPrivileges = loadPrivileges(context, u.rolesDn, tmParaPrivPath,	"groupOfNames", "cn");
			u.tmPacketPrivileges = loadPrivileges(context, u.rolesDn, tmPacketPrivPath, "groupOfNames", "cn");
			u.tcPrivileges = loadPrivileges(context, u.rolesDn, tcPrivPath,	"groupOfNames", "cn");
			u.systemPrivileges = loadPrivileges(context, u.rolesDn, systemPrivPath,	"groupOfNames", "cn");
		} finally {
			context.close();
		}
		log.debug("got user from ldap: " + u);
		return u;
	}

	Set<String> loadAssertedIdentities(DirContext context, String dn)
			throws NamingException {
		SearchControls cons = new SearchControls();
		cons.setSearchScope(SearchControls.SUBTREE_SCOPE);
		cons.setReturningAttributes(new String[] { "member" });
		NamingEnumeration<SearchResult> results;
		try {
			results = context.search("cn=assertedIdentities, " + dn,
					"member=*", cons);
		} catch (javax.naming.NameNotFoundException e) {
			// cn=canassertIdentities doesn't even exist for this user
			return null;
		}
		if (!results.hasMore())
			return null;
		HashSet<String> assertedids = new HashSet<String>();
		SearchResult r = results.next();
		javax.naming.directory.Attribute a = r.getAttributes().get("member");
		for (int i = 0; i < a.size(); i++) {
			assertedids.add((String) a.get(i));
		}
		return assertedids;
	}

	Set<String> loadRoles(DirContext context, String dn) throws NamingException {
		SearchControls cons = new SearchControls();
		cons.setSearchScope(SearchControls.SUBTREE_SCOPE);
		cons.setReturningAttributes(new String[] { "cn" });
		NamingEnumeration<SearchResult> results = context.search(rolePath, "member={0}", new String[] { dn }, cons);

		if (!results.hasMore())
			return null;

		HashSet<String> roles = new HashSet<String>();

		while (results.hasMore()) {
			SearchResult r = results.next();
			roles.add(r.getNameInNamespace());
		}
		return roles;
	}

	Set<String> loadPrivileges(DirContext context, Set<String> roles, String privPath, String objectClass, String attribute) throws NamingException {
		Set<String> privs = new HashSet<String>();
		StringBuffer sb = new StringBuffer();
		SearchControls cons = new SearchControls();
		cons.setSearchScope(SearchControls.SUBTREE_SCOPE);
		cons.setReturningAttributes(new String[] { attribute });

		sb.append("(&(objectClass=" + objectClass + ")(|");
		for (int i = 0; i < roles.size(); i++) {
			sb.append("(member={" + i + "})");
		}
		sb.append("))");
		NamingEnumeration<SearchResult> results = context.search(privPath, sb.toString(), roles.toArray(), cons);
		while (results.hasMore()) {
			SearchResult r = results.next();
			privs.add((String) r.getAttributes().get("cn").get());
		}

		return privs;
	}

	Pattern dn2userPattern = Pattern.compile("\\w+=([^,]+),.*");

	/**
	 * Returns the username corresponding to a certificate. If the privileges
	 * are enabled, the user is looked up in the LDAP. If privileges are
	 * disabled, it simply returns the first component of the certificate
	 * principal.
	 * 
	 * @param cert
	 * @return username
	 * @throws NamingException
	 */
	public String getUsernameFromCertificate(X509Certificate cert) {
		String dn = null;
		if (usePrivileges) {
			try {
				dn = getUserDnFromCertificate(cert);
			} catch (NamingException e) {
				log.warn("Naming exception caught when retrieving the userdn from certificate"
								+ cert.getSubjectDN());
				return null;
			}
			if (dn == null)
				return null;
		} else {
			dn = cert.getSubjectX500Principal().getName();
		}
		Matcher m = dn2userPattern.matcher(dn);
		if (m.matches())
			return m.group(1);
		else
			return null;
	}

	protected synchronized String getUserDnFromCertificate(X509Certificate cert) throws NamingException {
		UsernameCache uc = certCache.get(cert);
		if (uc != null) {
			if (System.currentTimeMillis() - uc.lastUpdated < CERT2USERNAME_CACHE_TIME)
				return uc.username;
			else
				certCache.remove(cert);
		}
		byte[] encodedCert = null;
		try {
			encodedCert = cert.getEncoded();
		} catch (java.security.cert.CertificateEncodingException e) {
			log	.warn("got CertificateEncodingException when encoding certificate:"+ cert);
			return null;
		}

		DirContext context = new InitialDirContext(contextEnv);
		try {
			SearchControls cons = new SearchControls();
			cons.setSearchScope(SearchControls.SUBTREE_SCOPE);
			cons.setReturningAttributes(new String[] { "userCertificate" });
			NamingEnumeration<SearchResult> results = context.search(userPath,"userCertificate=*", cons);
			boolean found = false;
			String uid = null;
			while (results.hasMore()) {
				SearchResult r = results.next();
				uid = r.getNameInNamespace();
				javax.naming.directory.Attribute a = r.getAttributes().get(
				"userCertificate;binary");
				if (a != null) {
					for (int i = 0; i < a.size(); i++) {
						if (Arrays.equals(encodedCert, (byte[]) a.get(i))) {
							found = true;
							break;
						}
					}
				}
				if (found)
					break;
			}
			String username = found ? uid : null;
			uc = new UsernameCache(username, System.currentTimeMillis());
			certCache.put(cert, uc);
			return username;
		} finally {
			context.close();
		}
	}

	public String getUserPath() {
	    return userPath;
	}
	/**
	 * @param args
	 * @throws NamingException
	 * @throws InterruptedException
	 * @throws ConfigurationException
	 */
	public static void main(String[] args) throws Exception {
		if (args.length < 1 || args.length > 2) {
			System.err.println("usage: print-priv.sh username | -f certificate.pem");
			System.exit(-1);
		}
		Privilege priv = Privilege.getInstance();
		long start = System.currentTimeMillis();
		int n = 1;
		for (int i = 0; i < n; i++) {
			try {
				if (args[0].equals("-f")) {
					java.io.FileInputStream fis = new java.io.FileInputStream(args[1]);
					java.io.BufferedInputStream bis = new java.io.BufferedInputStream(fis);

					java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");

					while (bis.available() > 0) {
						X509Certificate cert = (X509Certificate) cf.generateCertificate(bis);
						System.out.println("\n*******For certificate "+ cert.getIssuerX500Principal() + ":");
						String dn = priv.getUserDnFromCertificate(cert);
						if (dn == null)
							System.out.println("found no user");
						else
							System.out.println(priv.getUser(dn));
					}
				} else {
					String dn = "uid=" + args[0] + "," + priv.userPath;
					System.out.println(priv.getUser(dn));
				}
			} catch (NamingException e) {
				System.err.println("got NamingException: "+e);
			}
			Thread.sleep(1000);
		}
		long end = System.currentTimeMillis();
		System.err.println("took " + (end - start) + " milisec");
	}

	public int getMaxNoSessions() {
		return maxNoSessions;
	}

}

class User {
	String dn;
	long lastUpdated;
	Set<String> assertedIdentities;
	Set<String> rolesDn;
	Set<String> tmParaPrivileges;
	Set<String> tmPacketPrivileges;
	Set<String> tcPrivileges;
	Set<String> systemPrivileges;

	public String toString() {
		return "dn:" + dn + "\n can assert identities: " + assertedIdentities
				+ "\n roles: " + rolesDn + "\n   tm parameter privileges:"
				+ tmParaPrivileges + "\n   tm packet privileges:"
				+ tmPacketPrivileges + "\n   tc privileges:" + tcPrivileges
				+ "\n   system privileges:" + systemPrivileges
				+ "\n   lastUpdated:" + new Date(lastUpdated);
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
