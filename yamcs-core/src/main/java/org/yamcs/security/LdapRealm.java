package org.yamcs.security;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

/**
 * Created by msc on 05/05/15.
 */
public class LdapRealm implements Realm {

    private String tmParaPrivPath;
    private String tmParaSetPrivPath;
    private String tmPacketPrivPath;
    private String tcPrivPath;
    private String systemPrivPath;
    private String streamPrivPath;
    private String cmdHistoryPrivPath;
    private String rolePath;
    private String userPath;

    private static final Hashtable<String, String> contextEnv = new Hashtable<>();
    private static final Logger log = LoggerFactory.getLogger(LdapRealm.class);

    public LdapRealm() {
        YConfiguration conf = YConfiguration.getConfiguration("privileges");

        String host = conf.getString("ldaphost");
        userPath = conf.getString("userPath");
        rolePath = conf.getString("rolePath");
        if (conf.containsKey("systemPath")) {
            systemPrivPath = conf.getString("systemPath");
        }
        if (conf.containsKey("tmParameterPath")) {
            tmParaPrivPath = conf.getString("tmParameterPath");
        }
        if (conf.containsKey("tmParameterSetPath")) {
            tmParaSetPrivPath = conf.getString("tmParameterSetPath");
        }
        if (conf.containsKey("tmPacketPath")) {
            tmPacketPrivPath = conf.getString("tmPacketPath");
        }
        if (conf.containsKey("tcPath")) {
            tcPrivPath = conf.getString("tcPath");
        }
        if (conf.containsKey("streamPath")) {
            streamPrivPath = conf.getString("streamPath");
        }
        if (conf.containsKey("cmdHistoryPath")) {
            cmdHistoryPrivPath = conf.getString("cmdHistoryPath");
        }
        contextEnv.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        contextEnv.put(Context.PROVIDER_URL, "ldap://" + host);
        contextEnv.put("com.sun.jndi.ldap.connect.pool", "true");
    }

    @Override
    public boolean supports(AuthenticationToken authToken) {
        return authToken instanceof UsernamePasswordToken
                || authToken instanceof AccessToken
                || authToken instanceof CertificateToken;
    }

    @Override
    public boolean authenticate(AuthenticationToken authToken) {
        if (authToken == null || authToken.getPrincipal() == null) {
            return false;
        }
        if (authToken instanceof UsernamePasswordToken) {
            return authenticateUsernamePassword((UsernamePasswordToken) authToken);
        } else if (authToken instanceof CertificateToken) {
            return authenticateCertificate((CertificateToken) authToken);
        } else if (authToken instanceof AccessToken) {
            return authenticateAccessToken((AccessToken) authToken);
        }
        return false;
    }

    private boolean authenticateCertificate(CertificateToken authenticationToken) {
        try {
            X509Certificate cert = authenticationToken.getCert();
            byte[] encodedCert = null;
            try {
                encodedCert = cert.getEncoded();
            } catch (CertificateEncodingException e) {
                log.warn("got CertificateEncodingException when encoding certificate: {}", cert, e);
                return false;
            }

            DirContext context = new InitialDirContext(contextEnv);
            try {
                SearchControls cons = new SearchControls();
                cons.setSearchScope(SearchControls.SUBTREE_SCOPE);
                cons.setReturningAttributes(new String[] { "userCertificate" });
                NamingEnumeration<SearchResult> results = context.search(userPath, "userCertificate=*", cons);
                boolean found = false;
                while (results.hasMore()) {
                    SearchResult r = results.next();
                    // uid = r.getNameInNamespace();
                    Attribute a = r.getAttributes().get("userCertificate;binary");
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
        } catch (NamingException ne) {
            log.error("Unable to authenticate this X509Certificate certificate against LDAP.", ne);
            return false;
        }
    }

    private boolean authenticateAccessToken(AccessToken authToken) {
        User user = loadUser(authToken);
        return user != null && !authToken.isExpired();
    }

    private boolean authenticateUsernamePassword(UsernamePasswordToken authToken) {
        String username = authToken.getUsername();
        String password = authToken.getPasswordS();
        DirContext ctx = null;
        try {
            String userDn = "uid=" + username + "," + userPath;
            Hashtable<String, String> localContextEnv = new Hashtable<>();
            localContextEnv.put(Context.INITIAL_CONTEXT_FACTORY, contextEnv.get(Context.INITIAL_CONTEXT_FACTORY));
            localContextEnv.put(Context.PROVIDER_URL, contextEnv.get(Context.PROVIDER_URL));
            localContextEnv.put("com.sun.jndi.ldap.connect.pool", "true");
            localContextEnv.put(Context.SECURITY_AUTHENTICATION, "simple");
            localContextEnv.put(Context.SECURITY_PRINCIPAL, userDn);
            if (password != null) {
                localContextEnv.put(Context.SECURITY_CREDENTIALS, password);
            }
            ctx = new InitialDirContext(localContextEnv);
            ctx.close();
        } catch (AuthenticationException e) {
            log.warn("User '{}' not authenticated with LDAP; Could not bind with supplied username and password.",
                    username);
            return false;
        } catch (NamingException e) {
            log.warn("User '{}' not authenticated with LDAP; An LDAP error was caught: {}", username, e);
            return false;
        }
        return true;
    }

    /**
     * Loads a user from LDAP together with all the roles and the privileges.
     */
    @Override
    public User loadUser(AuthenticationToken authToken) {
        User u = new User(authToken);

        DirContext context = null;
        try {
            context = new InitialDirContext(contextEnv);
            String dn = "uid=" + u.getPrincipalName() + "," + userPath;
            Set<String> ldapRoles = loadRoles(context, dn);
            for (String ldapRole : ldapRoles) {
                int start = ldapRole.indexOf("cn=");
                int stop = ldapRole.indexOf(",ou=");
                u.addRole(ldapRole.substring(start + 3, stop));
            }
            if (tmParaPrivPath != null) {
                for (String privilege : loadPrivileges(context, ldapRoles, tmParaPrivPath)) {
                    u.addTmParaPrivilege(privilege);
                }
            }
            if (tmPacketPrivPath != null) {
                for (String privilege : loadPrivileges(context, ldapRoles, tmPacketPrivPath)) {
                    u.addTmPacketPrivilege(privilege);
                }
            }
            if (tcPrivPath != null) {
                for (String privilege : loadPrivileges(context, ldapRoles, tcPrivPath)) {
                    u.addTcPrivilege(privilege);
                }
            }
            if (systemPrivPath != null) {
                for (String privilege : loadPrivileges(context, ldapRoles, systemPrivPath)) {
                    u.addSystemPrivilege(privilege);
                }
            }
            if (tmParaSetPrivPath != null) {
                for (String privilege : loadPrivileges(context, ldapRoles, tmParaSetPrivPath)) {
                    u.addTmParaSetPrivilege(privilege);
                }
            }
            if (streamPrivPath != null) {
                for (String privilege : loadPrivileges(context, ldapRoles, streamPrivPath)) {
                    u.addStreamPrivilege(privilege);
                }
            }
            if (cmdHistoryPrivPath != null) {
                for (String privilege : loadPrivileges(context, ldapRoles, cmdHistoryPrivPath)) {
                    u.addCmdHistoryPrivilege(privilege);
                }
            }
        } catch (NamingException e) {
            throw new ConfigurationException(e);
        } finally {
            try {
                context.close();
            } catch (NamingException e) {
                log.error("Failed to close LDAP context", e);
            }
        }

        log.debug("got user from ldap: {}", u);
        return u;
    }

    private Set<String> loadRoles(DirContext context, String dn) throws NamingException {
        SearchControls cons = new SearchControls();
        cons.setSearchScope(SearchControls.SUBTREE_SCOPE);
        cons.setReturningAttributes(new String[] { "cn" });
        NamingEnumeration<SearchResult> results = context.search(rolePath, "member={0}", new String[] { dn }, cons);

        if (!results.hasMore()) {
            return null;
        }

        HashSet<String> roles = new HashSet<>();

        while (results.hasMore()) {
            SearchResult r = results.next();
            roles.add(r.getNameInNamespace());
        }
        return roles;
    }

    private Set<String> loadPrivileges(DirContext context, Set<String> roles, String privPath)
            throws NamingException {
        Set<String> privs = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        SearchControls cons = new SearchControls();
        cons.setSearchScope(SearchControls.SUBTREE_SCOPE);
        cons.setReturningAttributes(new String[] { "cn" });

        sb.append("(&(objectClass=groupOfNames)(|");
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
