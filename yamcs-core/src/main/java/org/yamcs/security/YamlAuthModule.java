package org.yamcs.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.utils.YObjectLoader;

public class YamlAuthModule implements AuthModule {

    private boolean required;
    private PasswordHasher passwordHasher;
    private Map<String, Map<String, Object>> userDefs = new HashMap<>();
    private Map<String, Map<String, Object>> roleDefs = new HashMap<>();

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("required", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("hasher", OptionType.STRING);
        return spec;
    }

    @Override
    public void init(YConfiguration args) throws InitException {
        required = args.getBoolean("required");
        if (args.containsKey("hasher")) {
            String className = args.getString("hasher");
            passwordHasher = YObjectLoader.loadObject(className);
        }

        // Read from users.yaml
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

        // Read from roles.yaml
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
            String username = ((UsernamePasswordToken) token).getPrincipal();
            char[] password = ((UsernamePasswordToken) token).getPassword();

            Map<String, Object> userDef = userDefs.get(username);
            if (userDef == null || !userDef.containsKey("password")
                    || YConfiguration.getString(userDef, "password").trim().isEmpty()) {
                return null;
            }

            // Verify password
            String expected = YConfiguration.getString(userDef, "password");
            if (passwordHasher != null) {
                if (!passwordHasher.validatePassword(password, expected)) {
                    throw new AuthenticationException("Password does not match");
                }
            } else {
                if (!Arrays.equals(expected.toCharArray(), password)) {
                    throw new AuthenticationException("Password does not match");
                }
            }

            AuthenticationInfo authenticationInfo = new AuthenticationInfo(this, username);
            authenticationInfo.addExternalIdentity(getClass().getName(), username);

            String displayName = YConfiguration.getString(userDef, "displayName", "").trim();
            if (!displayName.isEmpty()) {
                authenticationInfo.setDisplayName(displayName);
            }

            String email = YConfiguration.getString(userDef, "email", "").trim();
            if (!email.isEmpty()) {
                authenticationInfo.setEmail(email);
            }

            return authenticationInfo;
        } else {
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public AuthorizationInfo getAuthorizationInfo(AuthenticationInfo authenticationInfo) throws AuthorizationException {
        String principal = authenticationInfo.getUsername();

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
                            } else if (!typeString.equals("default")) {
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
    public boolean verifyValidity(AuthenticationInfo authenticationInfo) {
        return true;
    }
}
