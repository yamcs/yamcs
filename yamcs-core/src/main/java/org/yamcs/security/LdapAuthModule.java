package org.yamcs.security;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;

public class LdapAuthModule implements AuthModule {

    private static final Logger log = LoggerFactory.getLogger(LdapAuthModule.class);

    private boolean tls;
    private String providerUrl;
    private String userFormat;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("host", OptionType.STRING).withRequired(true);
        spec.addOption("port", OptionType.INTEGER);
        spec.addOption("tls", OptionType.BOOLEAN);
        spec.addOption("userFormat", OptionType.STRING).withRequired(true);
        return spec;
    }

    @Override
    public void init(YConfiguration args) throws InitException {
        String host = args.getString("host");
        userFormat = args.getString("userFormat");
        if (!userFormat.contains("%s")) {
            throw new InitException("userFormat should contain %s as placeholder for the submitted login name");
        }

        tls = args.getBoolean("tls", false);
        if (tls) {
            int port = args.getInt("port", 636);
            providerUrl = String.format("ldaps://%s:%s", host, port);
        } else {
            int port = args.getInt("port", 389);
            providerUrl = String.format("ldap://%s:%s", host, port);
        }
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
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.PROVIDER_URL, providerUrl);
            env.put("com.sun.jndi.ldap.connect.pool", "true");
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, String.format(userFormat, username));
            env.put(Context.SECURITY_CREDENTIALS, new String(password));
            if (tls) {
                env.put(Context.SECURITY_PROTOCOL, "ssl");
            }
            try {
                DirContext ctx = new InitialDirContext(env);
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
        return new AuthorizationInfo();
    }

    @Override
    public boolean verifyValidity(AuthenticationInfo authenticationInfo) {
        return true;
    }
}
