package org.yamcs.security;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.Spec.OptionType;

public class LdapAuthModule implements AuthModule {

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
    private static final Logger log = LoggerFactory.getLogger(LdapAuthModule.class);

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("host", OptionType.STRING).withRequired(true);
        spec.addOption("userPath", OptionType.STRING).withRequired(true);
        spec.addOption("rolePath", OptionType.STRING).withRequired(true);
        spec.addOption("systemPath", OptionType.STRING);
        spec.addOption("tmParameterPath", OptionType.STRING);
        spec.addOption("tmParameterSetPath", OptionType.STRING);
        spec.addOption("tmPacketPath", OptionType.STRING);
        spec.addOption("tcPath", OptionType.STRING);
        spec.addOption("streamPath", OptionType.STRING);
        spec.addOption("cmdHistoryPath", OptionType.STRING);
        return spec;
    }

    @Override
    public void init(YConfiguration args) throws InitException {
        String host = args.getString("host");
        userPath = args.getString("userPath");
        rolePath = args.getString("rolePath");

        if (args.containsKey("systemPath")) {
            systemPrivPath = args.getString("systemPath");
        }
        if (args.containsKey("tmParameterPath")) {
            tmParaPrivPath = args.getString("tmParameterPath");
        }
        if (args.containsKey("tmParameterSetPath")) {
            tmParaSetPrivPath = args.getString("tmParameterSetPath");
        }
        if (args.containsKey("tmPacketPath")) {
            tmPacketPrivPath = args.getString("tmPacketPath");
        }
        if (args.containsKey("tcPath")) {
            tcPrivPath = args.getString("tcPath");
        }
        if (args.containsKey("streamPath")) {
            streamPrivPath = args.getString("streamPath");
        }
        if (args.containsKey("cmdHistoryPath")) {
            cmdHistoryPrivPath = args.getString("cmdHistoryPath");
        }
        contextEnv.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        contextEnv.put(Context.PROVIDER_URL, "ldap://" + host);
        contextEnv.put("com.sun.jndi.ldap.connect.pool", "true");
    }

    /*
     * Currently this method does not follow our conventions very well. Namely, it does not distinguish between a user
     * that cannot be found, or a user that could not provide correct credentials. Therefore we return null in both,
     * cases to not stop the login process, and allow other AuthModules to try to identify the user.
     * 
     * A proper solution would likely require binding with an administrative account, such that user existence
     * can be verified prior to verifying credentials.
     */
    @Override
    public AuthenticationInfo getAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        if (token instanceof UsernamePasswordToken) {
            String username = ((UsernamePasswordToken) token).getPrincipal();
            char[] password = ((UsernamePasswordToken) token).getPassword();
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
                    localContextEnv.put(Context.SECURITY_CREDENTIALS, new String(password));
                }
                ctx = new InitialDirContext(localContextEnv);
                ctx.close();
            } catch (javax.naming.AuthenticationException e) {
                log.debug("User cannot bind", e);
                return null;
            } catch (NamingException e) {
                throw new AuthenticationException(e);
            }
            return new AuthenticationInfo(this, username);
        } else {
            return null;
        }
    }

    @Override
    public AuthorizationInfo getAuthorizationInfo(AuthenticationInfo authenticationInfo) {
        String principal = authenticationInfo.getPrincipal();
        AuthorizationInfo authz = new AuthorizationInfo();

        DirContext context = null;
        try {
            context = new InitialDirContext(contextEnv);
            String dn = "uid=" + principal + "," + userPath;
            Set<String> ldapRoles = loadRoles(context, dn);
            if (systemPrivPath != null) {
                for (String privilege : loadPrivileges(context, ldapRoles, systemPrivPath)) {
                    authz.addSystemPrivilege(new SystemPrivilege(privilege));
                }
            }

            if (tmParaPrivPath != null) {
                for (String object : loadPrivileges(context, ldapRoles, tmParaPrivPath)) {
                    authz.addObjectPrivilege(new ObjectPrivilege(ObjectPrivilegeType.ReadParameter, object));
                }
            }
            if (tmPacketPrivPath != null) {
                for (String object : loadPrivileges(context, ldapRoles, tmPacketPrivPath)) {
                    authz.addObjectPrivilege(new ObjectPrivilege(ObjectPrivilegeType.ReadPacket, object));
                }
            }
            if (tcPrivPath != null) {
                for (String object : loadPrivileges(context, ldapRoles, tcPrivPath)) {
                    authz.addObjectPrivilege(new ObjectPrivilege(ObjectPrivilegeType.Command, object));
                }
            }
            if (tmParaSetPrivPath != null) {
                for (String object : loadPrivileges(context, ldapRoles, tmParaSetPrivPath)) {
                    authz.addObjectPrivilege(new ObjectPrivilege(ObjectPrivilegeType.WriteParameter, object));
                }
            }
            if (streamPrivPath != null) {
                for (String object : loadPrivileges(context, ldapRoles, streamPrivPath)) {
                    authz.addObjectPrivilege(new ObjectPrivilege(ObjectPrivilegeType.Stream, object));
                }
            }
            if (cmdHistoryPrivPath != null) {
                for (String object : loadPrivileges(context, ldapRoles, cmdHistoryPrivPath)) {
                    authz.addObjectPrivilege(new ObjectPrivilege(ObjectPrivilegeType.CommandHistory, object));
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

        return authz;
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

    @Override
    public boolean verifyValidity(User user) {
        return true;
    }
}
