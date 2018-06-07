package org.yamcs.security;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.utils.YObjectLoader;

public class YamlAuthModule implements AuthModule {

    private static final Logger log = LoggerFactory.getLogger(YamlAuthModule.class);

    private PasswordHasher passwordHasher;
    private Map<String, Map<String, Object>> userDefs = new HashMap<>();
    private Map<String, Map<String, Object>> roleDefs = new HashMap<>();

    public YamlAuthModule(Map<String, Object> config) throws IOException {
        if (config.containsKey("hasher")) {
            String className = YConfiguration.getString(config, "hasher");
            passwordHasher = YObjectLoader.loadObject(className);
        }
        if (YConfiguration.isDefined("users")) {
            YConfiguration yconf = YConfiguration.getConfiguration("users");
            Map<String, Object> userConfig = yconf.getRoot();
            for (String username : userConfig.keySet()) {
                userDefs.put(username, YConfiguration.getMap(userConfig, username));
            }
        }
        if (YConfiguration.isDefined("roles")) {
            YConfiguration yconf = YConfiguration.getConfiguration("roles");
            Map<String, Object> roleConfig = yconf.getRoot();
            for (String role : roleConfig.keySet()) {
                roleDefs.put(role, YConfiguration.getMap(roleConfig, role));
            }
        }
    }

    @Override
    public boolean supportsAuthenticate(String type) {
        return TYPE_USERPASS.equals(type);
    }

    @Override
    public AuthenticationInfo getAuthenticationInfo(String type, Object authObj) throws AuthenticationException {
        Map<String, String> m = (Map<String, String>) authObj;
        String username = m.get(USERNAME);
        String password = m.get(PASSWORD);

        Map<String, Object> userDef = userDefs.get(username);
        if (userDef == null || userDef.containsKey("password")
                || YConfiguration.getString(userDef, "password").trim().isEmpty()) {
            log.debug("User does not exist");
            return null;
        }

        // Verify password
        String expected = YConfiguration.getString(userDef, "password");
        if (passwordHasher != null) {
            try {
                if (!passwordHasher.validatePassword(password.toCharArray(), expected)) {
                    throw new AuthenticationException("Password does not match");
                }
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new AuthenticationException(e);
            }
        } else {
            if (!expected.equals(password)) {
                throw new AuthenticationException("Password does not match");
            }
        }

        return new AuthenticationInfo(this, username);
    }

    @Override
    public AuthorizationInfo getAuthorizationInfo(AuthenticationInfo authenticationInfo) {
        String principal = authenticationInfo.getPrincipal();

        AuthorizationInfo authz = new AuthorizationInfo();

        Map<String, Object> userDef = userDefs.get(principal);
        if (userDef != null) {
            if (YConfiguration.getBoolean(userDef, "superuser", false)) {
                authz.grantSuperuser();
            }
            if (userDef.containsKey("roles")) {
                List<String> roles = YConfiguration.getList(userDef, "roles");
                for (String role : roles) {
                    authz.addRole(role);

                    // Add privileges for this role
                    if (roleDefs.containsKey(role)) {
                        Map<String, Object> privs = roleDefs.get(role);
                        privs.forEach((priv, obj) -> authz.addPrivilege(priv, (String) obj));
                    }
                }
            }
        }

        return authz;
    }

    @Override
    public boolean verifyValidity(User user) {
        return true;
    }
}
