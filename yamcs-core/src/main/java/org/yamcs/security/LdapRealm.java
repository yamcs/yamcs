package org.yamcs.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

/**
 * Created by msc on 05/05/15.
 */
public class LdapRealm implements Realm {
    public static String tmParaPrivPath;
    public static String tmParaSetPrivPath;
    public static String tmPacketPrivPath;
    public static String tcPrivPath;
    public static String systemPrivPath;
    public static String rolePath;
    public static String userPath;

    static final Hashtable<String, String> contextEnv = new Hashtable<String, String>();
    static Logger log = LoggerFactory.getLogger(LdapRealm.class);

    static {
            YConfiguration conf = YConfiguration.getConfiguration("privileges");

            String host = conf.getString("ldaphost");
            userPath = conf.getString("userPath");
            rolePath = conf.getString("rolePath");
            systemPrivPath = conf.getString("systemPath");
            tmParaPrivPath = conf.getString("tmParameterPath");
            tmParaSetPrivPath = conf.getString("tmParameterSetPath");
            tmPacketPrivPath = conf.getString("tmPacketPath");
            tcPrivPath = conf.getString("tcPath");
            contextEnv.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            contextEnv.put(Context.PROVIDER_URL, "ldap://" + host);
            contextEnv.put("com.sun.jndi.ldap.connect.pool", "true");
    }

    private String getUserDn(User user) {
        String username = user.getAuthenticationToken().getPrincipal().toString();
        if (username == null)
            return null;
        return "uid=" + username + "," + userPath;
    }


//    public String[] getRoles(User u) {
//        Pattern dn2rolePattern=Pattern.compile("\\w+=([^,]+),.*");
//        if(u.roles==null) return null;
//        String[] roles=new String[u.roles.size()];
//        int i=0;
//        for(String roledn:u.roles) {
//            Matcher m=dn2rolePattern.matcher(roledn);
//            if(m.matches()) roles[i]=m.group(1);
//            else roles[i]=roledn;
//            i++;
//        }
//        return roles;
//    }

    /**
     * supports
     * @param authenticationToken
     * @return true if the authenticationToken is supported by this realm, false otherwise
     */
    public boolean supports(AuthenticationToken authenticationToken)
    {
        return authenticationToken.getClass() == UsernamePasswordToken.class
                || authenticationToken.getClass() == CertificateToken.class
                || authenticationToken.getClass() == HqClientMessageToken.class;
    }



    @Override
    public boolean authenticates(AuthenticationToken authenticationToken) {
        if(  authenticationToken == null
                || authenticationToken.getPrincipal() == null
                || authenticationToken.getCredentials() == null) {
            return false;
        }
        if(authenticationToken.getClass() == UsernamePasswordToken.class
                || authenticationToken.getClass() == HqClientMessageToken.class)
            return authenticateUsernamePassword((UsernamePasswordToken)authenticationToken);
        else if(authenticationToken.getClass() == CertificateToken.class)
            return authenticateCertificate((CertificateToken) authenticationToken);

        log.error("Authentication Token of type " + authenticationToken.getClass() + " is not supported by LDAP realm.");
        return false;
    }

    private boolean authenticateCertificate(CertificateToken authenticationToken)  {

        try {

            X509Certificate cert = authenticationToken.getCert();
            byte[] encodedCert = null;
            try {
                encodedCert = cert.getEncoded();
            } catch (java.security.cert.CertificateEncodingException e) {
                log.warn("got CertificateEncodingException when encoding certificate:" + cert);
                return false;
            }

            DirContext context = new InitialDirContext(contextEnv);
            try {
                SearchControls cons = new SearchControls();
                cons.setSearchScope(SearchControls.SUBTREE_SCOPE);
                cons.setReturningAttributes(new String[]{"userCertificate"});
                NamingEnumeration<SearchResult> results = context.search(userPath, "userCertificate=*", cons);
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
                return found;
            } finally {
                context.close();
            }
        }
        catch (NamingException ne)
        {
            log.error("Unable to authenticate this X509Certificate certificate against LDAP.", ne);
            return false;
        }
    }

    private boolean authenticateUsernamePassword(UsernamePasswordToken usernamePasswordToken)
    {
        String username = usernamePasswordToken.getUsername();
        String password = usernamePasswordToken.getPasswordS();
        DirContext ctx = null;
        try {
            String userDn = "uid="+username+","+userPath;
            Hashtable<String, String> localContextEnv = new Hashtable<>();
            localContextEnv.put(Context.INITIAL_CONTEXT_FACTORY, contextEnv.get(Context.INITIAL_CONTEXT_FACTORY));
            localContextEnv.put(Context.PROVIDER_URL, contextEnv.get(Context.PROVIDER_URL));
            localContextEnv.put("com.sun.jndi.ldap.connect.pool", "true");
            localContextEnv.put(Context.SECURITY_AUTHENTICATION, "simple");
            localContextEnv.put(Context.SECURITY_PRINCIPAL, userDn);
            if( password != null ) {
                localContextEnv.put(Context.SECURITY_CREDENTIALS, password);
            }
            ctx = new InitialDirContext( localContextEnv );
            ctx.close();
        } catch (AuthenticationException e) {
            log.warn( "User '{}' not authenticated with LDAP; Could not bind with supplied username and password.", username );
            return false;
        } catch (NamingException e) {
            log.warn( "User '{}' not authenticated with LDAP; An LDAP error was caught: {}", username, e );
            return false;
        }
        return true;
    }

    /**
     * Loads a user from LDAP together with all the roles and the privileges.
     *
     */
    @Override
    public User loadUser(AuthenticationToken authenticationToken)
    {
        log.info("");

        User u = new User(authenticationToken);
        u.lastUpdated = System.currentTimeMillis();

        // Load privileges
        DirContext context = null;
        try {
            context = new InitialDirContext(contextEnv);
        } catch (NamingException e) {
            log.error("", e);
            return null;
        }
        try {
            String dn = "uid=" + u.getPrincipalName() + "," + userPath;
            Set<String> ldapRoles =  loadRoles(context, dn);
            u.roles = ldapRolesToRoles(ldapRoles);
            if (u.roles == null)
                return u;
            u.tmParaPrivileges = loadPrivileges(context, ldapRoles, tmParaPrivPath,	"groupOfNames", "cn");
            u.tmPacketPrivileges = loadPrivileges(context, ldapRoles, tmPacketPrivPath, "groupOfNames", "cn");
            u.tcPrivileges = loadPrivileges(context, ldapRoles, tcPrivPath,	"groupOfNames", "cn");
            u.systemPrivileges = loadPrivileges(context, ldapRoles, systemPrivPath,	"groupOfNames", "cn");
            // might fail on previous yamcs ldap since this is a new type of privileges:
            u.tmParaSetPrivileges = loadPrivileges(context, ldapRoles, tmParaSetPrivPath,	"groupOfNames", "cn");

        } catch (NamingException e) {
            log.error("", e);
        } finally {
            try {
                context.close();
            } catch (NamingException e) {
                log.error("", e);
            }
        }

        // Check authentication
        u.setAuthenticated(this.authenticates(authenticationToken));

        log.debug("got user from ldap: " + u);
        return u;
    }

    /**
     * filter roles to remove ldap path
     * @param ldapRoles
     * @return
     */
    private Set<String> ldapRolesToRoles(Set<String> ldapRoles) {
        if(ldapRoles == null)
            return null;
        Set<String> roles = new HashSet<>();
        for(String ldapRole : ldapRoles) {
            try
            {
                int start = ldapRole.indexOf("cn=");
                int stop = ldapRole.indexOf(",ou=");
                roles.add(ldapRole.substring(start + 3, stop));
            }
            catch (Exception e){
                log.error("Unable to extract role from LDAP search result");
            }
        }
        return roles;
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




    public String getUserPath() {
        return userPath;
    }


}
