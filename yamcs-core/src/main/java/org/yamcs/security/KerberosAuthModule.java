package org.yamcs.security;

import static javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag.REQUIRED;

import java.io.IOException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AccountNotFoundException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;

/**
 * Does password-based login against a Kerberos host.
 */
public class KerberosAuthModule implements AuthModule {

    private static final Logger log = LoggerFactory.getLogger(KerberosAuthModule.class);
    private static final String JAAS_ENTRY_NAME = "Yamcs";
    private static final String JAAS_KRB5 = "com.sun.security.auth.module.Krb5LoginModule";

    @Override
    public Spec getSpec() {
        var spec = new Spec();
        spec.addOption("debug", OptionType.BOOLEAN).withDefault(false);
        return spec;
    }

    @Override
    public void init(YConfiguration args) throws InitException {
        Map<String, String> jaasOpts = new HashMap<>();
        jaasOpts.put("useKeyTab", "false");
        jaasOpts.put("useTicketCache", "false");
        jaasOpts.put("debug", Boolean.toString(args.getBoolean("debug")));

        AppConfigurationEntry jaasEntry = new AppConfigurationEntry(JAAS_KRB5, REQUIRED, jaasOpts);
        JaasConfiguration.addEntry(JAAS_ENTRY_NAME, jaasEntry);
    }

    @Override
    public AuthenticationInfo getAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        if (token instanceof UsernamePasswordToken) {
            return authenticateByPassword((UsernamePasswordToken) token);
        } else {
            return null;
        }
    }

    private AuthenticationInfo authenticateByPassword(UsernamePasswordToken token) throws AuthenticationException {
        String username = token.getPrincipal();
        char[] password = token.getPassword();
        try {
            LoginContext userLogin = new LoginContext(JAAS_ENTRY_NAME, new UserPassCallbackHandler(username, password));
            userLogin.login();
            AuthenticationInfo authenticationInfo = new AuthenticationInfo(this, username);
            Principal identity = userLogin.getSubject().getPrincipals().iterator().next();
            authenticationInfo.addExternalIdentity(getClass().getName(), identity.getName());
            return authenticationInfo;
        } catch (AccountNotFoundException e) {
            return null;
        } catch (LoginException e) {
            throw new AuthenticationException(e);
        }
    }

    @Override
    public boolean verifyValidity(AuthenticationInfo authenticationInfo) {
        return true;
    }

    @Override
    public AuthorizationInfo getAuthorizationInfo(AuthenticationInfo authenticationInfo) {
        return new AuthorizationInfo();
    }

    private static class UserPassCallbackHandler implements CallbackHandler {
        private char[] password;
        private String username;

        public UserPassCallbackHandler(String name, char[] password) {
            super();
            this.username = name;
            this.password = password;
        }

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback && username != null) {
                    NameCallback nc = (NameCallback) callback;
                    nc.setName(username);
                } else if (callback instanceof PasswordCallback) {
                    PasswordCallback pc = (PasswordCallback) callback;
                    pc.setPassword(password);
                } else {
                    log.warn("Unrecognized callback " + callback);
                }
            }
        }
    }
}
