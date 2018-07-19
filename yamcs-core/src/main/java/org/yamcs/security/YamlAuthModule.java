package org.yamcs.security;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.utils.YObjectLoader;

public class YamlAuthModule implements AuthModule {

    private static final Logger log = LoggerFactory.getLogger(YamlAuthModule.class);

    private boolean required;
    private PasswordHasher passwordHasher;
    private Map<String, Map<String, Object>> userDefs = new HashMap<>();
    private Map<String, Map<String, Object>> roleDefs = new HashMap<>();

    public YamlAuthModule() throws IOException {
        this(Collections.emptyMap());
    }

    public YamlAuthModule(Map<String, Object> config) throws IOException {
        required = YConfiguration.getBoolean(config, "required", false);
        if (config.containsKey("hasher")) {
            String className = YConfiguration.getString(config, "hasher");
            passwordHasher = YObjectLoader.loadObject(className);
        }
        if (YConfiguration.isDefined("users")) {
            YConfiguration yconf = YConfiguration.getConfiguration("users");
            Map<String, Object> userConfig = yconf.getRoot();
            for (String username : userConfig.keySet()) {
                if (!YConfiguration.isNull(userConfig, username)) {
                    userDefs.put(username, YConfiguration.getMap(userConfig, username));
                } else {
                    userDefs.put(username, Collections.emptyMap());
                }
            }
        }
        if (YConfiguration.isDefined("roles")) {
            YConfiguration yconf = YConfiguration.getConfiguration("roles");
            Map<String, Object> roleConfig = yconf.getRoot();
            for (String role : roleConfig.keySet()) {
                if (!YConfiguration.isNull(roleConfig, role)) {
                    roleDefs.put(role, YConfiguration.getMap(roleConfig, role));
                }
            }
        }
    }

    @Override
    public AuthenticationInfo getAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        if (token instanceof UsernamePasswordToken) {
            String username = token.getPrincipal();
            char[] password = ((UsernamePasswordToken) token).getPassword();

            Map<String, Object> userDef = userDefs.get(username);
            if (userDef == null || !userDef.containsKey("password")
                    || YConfiguration.getString(userDef, "password").trim().isEmpty()) {
                log.debug("User does not exist");
                return null;
            }

            // Verify password
            String expected = YConfiguration.getString(userDef, "password");
            if (passwordHasher != null) {
                try {
                    if (!passwordHasher.validatePassword(password, expected)) {
                        throw new AuthenticationException("Password does not match");
                    }
                } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                    throw new AuthenticationException(e);
                }
            } else {
                if (!Arrays.equals(expected.toCharArray(), password)) {
                    throw new AuthenticationException("Password does not match");
                }
            }
            return new AuthenticationInfo(this, username);
        } else {
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public AuthorizationInfo getAuthorizationInfo(AuthenticationInfo authenticationInfo) throws AuthorizationException {
        String principal = authenticationInfo.getPrincipal();

        AuthorizationInfo authz = new AuthorizationInfo();

        Map<String, Object> userDef = userDefs.get(principal);
        if (userDef == null) {
            if (required) {
                throw new AuthorizationException("Cannot find user '" + principal + "' in users.yaml");
            }
        } else {
            if (YConfiguration.getBoolean(userDef, "superuser", false)) {
                authz.grantSuperuser();
            }
            if (userDef.containsKey("roles")) {
                List<String> roles = YConfiguration.getList(userDef, "roles");
                for (String role : roles) {

                    // Add privileges for this role
                    if (roleDefs.containsKey(role)) {
                        Map<String, Object> types = roleDefs.get(role);
                        types.forEach((typeString, objects) -> {
                            if (typeString.equals("System")) {
                                for (String name : (List<String>) objects) {
                                    authz.addSystemPrivilege(new SystemPrivilege(name));
                                }
                            } else {
                                ObjectPrivilegeType type = new ObjectPrivilegeType(typeString);
                                for (String object : (List<String>) objects) {
                                    authz.addObjectPrivilege(new ObjectPrivilege(type, object));
                                }
                            }
                        });
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
