package org.yamcs.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.logging.Log;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class LdapAuthModule implements AuthModule {

    private Log log = new Log(LdapAuthModule.class);

    private boolean tls;
    private String providerUrl;
    private Hashtable<String, String> yamcsEnv;

    private String userBase;
    private String nameAttribute;
    private String userFilter;
    private String[] displayNameAttributes;
    private String[] emailAttributes;
    private String[] searchAttributes;
    private List<GroupMapping> groupMappings = new ArrayList<>();

    private List<String> groupBase;
    private String groupFilter;
    private String groupFilterUserAttribute;

    private boolean requiredIfKerberos;

    private Cache<String, LdapUserInfo> infoCache = CacheBuilder.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build();

    @Override
    public Spec getSpec() {
        Spec attributesSpec = new Spec();
        attributesSpec.addOption("name", OptionType.STRING)
                .withDefault("uid");
        attributesSpec.addOption("email", OptionType.LIST_OR_ELEMENT)
                .withElementType(OptionType.STRING)
                .withDefault(Arrays.asList("mail", "email", "userPrincipalName"));
        attributesSpec.addOption("displayName", OptionType.LIST_OR_ELEMENT)
                .withElementType(OptionType.STRING)
                .withDefault("cn");

        Spec groupMappingSpec = new Spec();
        groupMappingSpec.addOption("dn", OptionType.STRING).withRequired(true);
        groupMappingSpec.addOption("role", OptionType.STRING);
        groupMappingSpec.addOption("superuser", OptionType.BOOLEAN);
        groupMappingSpec.requireOneOf("role", "superuser");

        Spec spec = new Spec();
        spec.addOption("host", OptionType.STRING).withRequired(true);
        spec.addOption("port", OptionType.INTEGER);
        spec.addOption("user", OptionType.STRING);
        spec.addOption("password", OptionType.STRING).withSecret(true);
        spec.requireTogether("user", "password");
        spec.addOption("tls", OptionType.BOOLEAN);
        spec.addOption("userBase", OptionType.STRING).withRequired(true);
        spec.addOption("attributes", OptionType.MAP)
                .withSpec(attributesSpec)
                .withApplySpecDefaults(true);
        spec.addOption("userFilter", OptionType.STRING);
        spec.addOption("groupMappings", OptionType.LIST)
                .withElementType(OptionType.MAP)
                .withSpec(groupMappingSpec);

        spec.addOption("groupBase", OptionType.LIST_OR_ELEMENT).withElementType(OptionType.STRING);
        spec.addOption("groupFilter", OptionType.STRING);
        spec.addOption("groupFilterUserAttribute", OptionType.STRING);
        spec.requireTogether("groupBase", "groupFilter", "groupFilterUserAttribute");

        spec.addOption("requiredIfKerberos", OptionType.BOOLEAN).withDefault(false);

        return spec;
    }

    @Override
    public void init(YConfiguration args) throws InitException {
        String host = args.getString("host");

        tls = args.getBoolean("tls", false);
        if (tls) {
            int port = args.getInt("port", 636);
            providerUrl = String.format("ldaps://%s:%s", host, port);
        } else {
            int port = args.getInt("port", 389);
            providerUrl = String.format("ldap://%s:%s", host, port);
        }

        userBase = args.getString("userBase");

        YConfiguration attributesArgs = args.getConfig("attributes");
        nameAttribute = attributesArgs.getString("name");

        userFilter = args.getString("userFilter", "(" + nameAttribute + "={0})");
        if (!userFilter.contains("{0}")) {
            throw new InitException("LDAP user filter should contain the {0} character sequence, "
                    + "which will be replaced with the attempted username");
        }

        displayNameAttributes = attributesArgs.getList("displayName").toArray(new String[0]);
        emailAttributes = attributesArgs.getList("email").toArray(new String[0]);

        groupBase = args.containsKey("groupBase") ? args.getList("groupBase") : null;
        groupFilter = args.getString("groupFilter", null);
        groupFilterUserAttribute = args.getString("groupFilterUserAttribute", null);

        if (args.containsKey("groupMappings")) {
            for (var mappingConfig : args.getConfigList("groupMappings")) {
                var groupMapping = new GroupMapping();
                groupMapping.dn = mappingConfig.getString("dn");
                groupMapping.role = mappingConfig.getString("role", null);
                groupMapping.superuser = mappingConfig.getBoolean("superuser", false);
                groupMappings.add(groupMapping);
            }
        }

        var concat = new HashSet<String>();
        concat.add(nameAttribute);
        concat.addAll(attributesArgs.getList("displayName"));
        concat.addAll(attributesArgs.getList("email"));
        concat.add("memberOf");
        if (groupFilterUserAttribute != null) {
            concat.add(groupFilterUserAttribute);
        }
        searchAttributes = concat.toArray(new String[0]);

        requiredIfKerberos = args.getBoolean("requiredIfKerberos");

        yamcsEnv = new Hashtable<>();
        yamcsEnv.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        yamcsEnv.put(Context.PROVIDER_URL, providerUrl);

        // Referral is needed to support querying of memberOf attribute that is
        // generated through use of dynlist overlay.
        yamcsEnv.put(Context.REFERRAL, "follow");

        yamcsEnv.put(Context.SECURITY_AUTHENTICATION, "simple");
        if (args.containsKey("user")) {
            yamcsEnv.put(Context.SECURITY_PRINCIPAL, args.getString("user"));
        }
        if (args.containsKey("password")) {
            yamcsEnv.put(Context.SECURITY_CREDENTIALS, args.getString("password"));
        }
        if (tls) {
            yamcsEnv.put(Context.SECURITY_PROTOCOL, "ssl");
        }
    }

    @Override
    public AuthenticationInfo getAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        if (token instanceof UsernamePasswordToken) {
            String username = ((UsernamePasswordToken) token).getPrincipal();
            char[] password = ((UsernamePasswordToken) token).getPassword();

            LdapUserInfo info;
            try {
                info = searchUserInfo(username);
            } catch (NamingException e) {
                log.warn("Failed to search LDAP for user {}", username, e);
                return null;
            }

            if (info == null) {
                return null;
            }

            bindUser(info.dn, password);
            AuthenticationInfo authenticationInfo = new AuthenticationInfo(this, info.uid);
            authenticationInfo.addExternalIdentity(getClass().getName(), info.dn);
            authenticationInfo.setDisplayName(info.cn);
            authenticationInfo.setEmail(info.email);
            return authenticationInfo;
        } else {
            return null;
        }
    }

    @Override
    public void authenticationSucceeded(AuthenticationInfo authenticationInfo) {
        if (authenticationInfo.isKerberos()) {
            // Note to future self: If we ever want to support multiple LDAP and
            // kerberos modules, then it may become useful to compare the user dn
            // with the kerberos realm before querying LDAP.
            String username = authenticationInfo.getUsername();
            try {
                LdapUserInfo info = searchUserInfo(username);
                if (info == null) {
                    log.warn("User {} not found in LDAP", username);
                } else {
                    authenticationInfo.addExternalIdentity(getClass().getName(), info.dn);
                    authenticationInfo.setDisplayName(info.cn);
                    authenticationInfo.setEmail(info.email);
                }
            } catch (NamingException e) {
                log.warn("Failed to search LDAP for user {}", username, e);
            }
        }
    }

    private LdapUserInfo searchUserInfo(String username) throws NamingException {
        LdapUserInfo info = infoCache.getIfPresent(username);
        if (info != null) {
            return info;
        }

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(yamcsEnv);
            var controls = new SearchControls();
            controls.setReturningAttributes(searchAttributes);
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            var filter = userFilter.replace("{0}", username);
            var searchResult = getSingleResult(ctx, userBase, filter, controls);
            if (searchResult == null) {
                return null;
            }
            info = new LdapUserInfo();
            // Use the uid from LDAP, just to prevent case sensitivity issues.
            info.uid = (String) searchResult.getAttributes().get(nameAttribute).get();
            info.dn = searchResult.getNameInNamespace();
            info.cn = findAttribute(searchResult, displayNameAttributes);
            info.email = findAttribute(searchResult, emailAttributes);
            info.memberOf = findListAttribute(searchResult, new String[] { "memberOf" });

            if (groupBase != null) {
                controls = new SearchControls();
                controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
                var lookup = findAttribute(searchResult, new String[] { groupFilterUserAttribute });
                if (lookup == null && "dn".equalsIgnoreCase(groupFilterUserAttribute)) {
                    lookup = searchResult.getNameInNamespace();
                }
                if (lookup != null) {
                    filter = groupFilter.replace("{0}", lookup);
                    for (var groupBaseElement : groupBase) {
                        var answer = ctx.search(groupBaseElement, filter, controls);
                        while (answer.hasMore()) {
                            searchResult = answer.next();
                            info.memberOf.add(searchResult.getNameInNamespace());
                        }
                        answer.close();
                    }
                }
            }

            infoCache.put(username, info);
            return info;
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
    }

    private void bindUser(String dn, char[] password) throws AuthenticationException {
        // Never bind with empty password, because on many LDAP servers
        // this would make a successful "unauthenticated" simple bind.
        // https://datatracker.ietf.org/doc/html/rfc4513#section-5.1.2
        if (password.length == 0) {
            throw new AuthenticationException("Invalid password (empty)");
        }

        var env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, providerUrl);
        env.put("com.sun.jndi.ldap.connect.pool", "true");
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, dn);
        env.put(Context.SECURITY_CREDENTIALS, new String(password));
        if (tls) {
            env.put(Context.SECURITY_PROTOCOL, "ssl");
        }
        try {
            DirContext ctx = new InitialDirContext(env);
            ctx.close();
        } catch (javax.naming.AuthenticationException e) {
            log.warn("Bind failed for dn '{}'", dn, e);
            throw new AuthenticationException("Invalid password");
        } catch (NamingException e) {
            throw new AuthenticationException(e);
        }
    }

    @Override
    public AuthorizationInfo getAuthorizationInfo(AuthenticationInfo authenticationInfo) throws AuthorizationException {
        AuthorizationInfo authz = new AuthorizationInfo();

        var principal = authenticationInfo.getUsername();
        var info = infoCache.getIfPresent(principal);

        if (authenticationInfo.isKerberos() && requiredIfKerberos && info == null) {
            throw new AuthorizationException("Cannot link Kerberos user with LDAP directory");
        }

        if (info != null) {
            for (var groupMapping : groupMappings) {
                for (var dn : info.memberOf) {
                    if (groupMapping.dn.equalsIgnoreCase(dn)) {
                        if (groupMapping.role != null) {
                            authz.addRole(groupMapping.role);
                        }
                        if (groupMapping.superuser) {
                            authz.grantSuperuser();
                        }
                    }
                }
            }
        }

        return authz;
    }

    @Override
    public boolean verifyValidity(AuthenticationInfo authenticationInfo) {
        return true;
    }

    private SearchResult getSingleResult(DirContext ctx, String searchBase, String filter, SearchControls controls)
            throws NamingException {
        var answer = ctx.search(searchBase, filter, controls);
        if (answer.hasMore()) {
            var result = answer.next();
            answer.close();
            return result;
        }
        return null;
    }

    private String findAttribute(SearchResult result, String[] possibleNames) throws NamingException {
        for (String attrId : possibleNames) {
            Attribute attr = result.getAttributes().get(attrId);
            if (attr != null) {
                return (String) attr.get();
            }
        }
        return null;
    }

    private List<String> findListAttribute(SearchResult result, String[] possibleNames) throws NamingException {
        for (String attrId : possibleNames) {
            var values = new ArrayList<String>();
            var attr = result.getAttributes().get(attrId);
            if (attr != null) {
                var valueEnumeration = attr.getAll();
                while (valueEnumeration.hasMoreElements()) {
                    values.add((String) valueEnumeration.next());
                }
                return values;
            }
        }
        return new ArrayList<>();
    }

    private static final class GroupMapping {
        String dn;
        String role;
        boolean superuser;
    }

    private static final class LdapUserInfo {
        String uid;
        String dn;
        String cn;
        String email;
        List<String> memberOf;
    }
}
