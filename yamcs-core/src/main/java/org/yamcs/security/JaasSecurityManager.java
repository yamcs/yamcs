package org.yamcs.security;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;

/**
 * Security Manager that leverages JAAS login modules.
 * 
 * The Subject returned by the login context should have one UserPrincipal containing the username, and a set of
 * RolePrincipal for each role of the user.
 */
public class JaasSecurityManager implements YamcsSecurityManager {

    private static final Logger log = LoggerFactory.getLogger(JaasSecurityManager.class);

    private String domain;

    public JaasSecurityManager(Map<String, Object> config) {
        domain = YConfiguration.getString(config, "domain");
    }

    @Override
    public CompletableFuture<String> validateUser(String username, char[] password) {
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            LoginContext ctx = new LoginContext(domain, new JaasCallbackHandler(username, password));
            ctx.login();
            Subject subject = ctx.getSubject();
            String user = getUserFromSubject(subject);
            future.complete(user);
        } catch (LoginException e) {
            log.debug("Could not validate user {}", username);
            // Does NOT complete exceptionally.
            future.complete(null);
        }

        return future;
    }

    private String getUserFromSubject(Subject subject) {
        for (UserPrincipal userPrincipal : subject.getPrincipals(UserPrincipal.class)) {
            return userPrincipal.getName();
        }
        return "";
    }
}
