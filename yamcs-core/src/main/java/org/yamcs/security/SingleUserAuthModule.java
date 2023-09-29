package org.yamcs.security;

import java.util.Arrays;
import java.util.List;

import org.yamcs.Experimental;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.utils.YObjectLoader;

/**
 * An AuthModule that enforces a login of one fixed user account
 */
@Experimental
public class SingleUserAuthModule implements AuthModule {

    protected static final String OPTION_USERNAME = "username";
    protected static final String OPTION_PASSWORD = "password";
    protected static final String OPTION_NAME = "name";
    protected static final String OPTION_EMAIL = "email";
    protected static final String OPTION_SUPERUSER = "superuser";
    protected static final String OPTION_PRIVILEGES = "privileges";
    protected static final String OPTION_HASHER = "hasher";

    private AuthenticationInfo authenticationInfo;
    private AuthorizationInfo authorizationInfo;

    private PasswordHasher passwordHasher;
    private String expectedHash;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption(OPTION_USERNAME, OptionType.STRING).withRequired(true);
        spec.addOption(OPTION_PASSWORD, OptionType.STRING).withRequired(true).withSecret(true);
        spec.addOption(OPTION_NAME, OptionType.STRING);
        spec.addOption(OPTION_EMAIL, OptionType.STRING);
        spec.addOption(OPTION_SUPERUSER, OptionType.BOOLEAN).withDefault(false);
        spec.addOption(OPTION_PRIVILEGES, OptionType.ANY);
        spec.addOption(OPTION_HASHER, OptionType.STRING);
        return spec;
    }

    @Override
    public void init(YConfiguration args) throws InitException {
        String username = args.getString(OPTION_USERNAME);
        authenticationInfo = new AuthenticationInfo(this, username);

        expectedHash = args.getString(OPTION_PASSWORD);

        String name = args.getString(OPTION_USERNAME, username);
        authenticationInfo.setDisplayName(name);

        String email = args.getString(OPTION_EMAIL, null);
        authenticationInfo.setEmail(email);

        authorizationInfo = new AuthorizationInfo();
        if (args.getBoolean(OPTION_SUPERUSER)) {
            authorizationInfo.grantSuperuser();
        }
        if (args.containsKey(OPTION_PRIVILEGES)) {
            var privilegeConfigs = args.getConfig(OPTION_PRIVILEGES);
            for (String privilegeName : privilegeConfigs.getKeys()) {
                List<String> objects = privilegeConfigs.getList(privilegeName);
                if (privilegeName.equals("System")) {
                    for (String object : objects) {
                        authorizationInfo.addSystemPrivilege(new SystemPrivilege(object));
                    }
                } else {
                    var type = new ObjectPrivilegeType(privilegeName);
                    for (String object : objects) {
                        authorizationInfo.addObjectPrivilege(new ObjectPrivilege(type, object));
                    }
                }
            }
        }

        if (args.containsKey(OPTION_HASHER)) {
            String className = args.getString(OPTION_HASHER);
            passwordHasher = YObjectLoader.loadObject(className);
        }
    }

    @Override
    public AuthenticationInfo getAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        if (token instanceof UsernamePasswordToken) {
            String username = ((UsernamePasswordToken) token).getPrincipal();
            char[] password = ((UsernamePasswordToken) token).getPassword();

            if (!username.equals(authenticationInfo.getUsername())) {
                return null;
            }

            if (passwordHasher != null) {
                if (!passwordHasher.validatePassword(password, expectedHash)) {
                    throw new AuthenticationException("Password does not match");
                }
            } else {
                if (!Arrays.equals(expectedHash.toCharArray(), password)) {
                    throw new AuthenticationException("Password does not match");
                }
            }
            return authenticationInfo;
        } else {
            return null;
        }
    }

    @Override
    public AuthorizationInfo getAuthorizationInfo(AuthenticationInfo authenticationInfo) throws AuthorizationException {
        String incomingUsername = authenticationInfo.getUsername();
        if (incomingUsername.equals(this.authenticationInfo.getUsername())) {
            return authorizationInfo;
        } else {
            return new AuthorizationInfo();
        }
    }

    @Override
    public boolean verifyValidity(AuthenticationInfo authenticationInfo) {
        return this.authenticationInfo.equals(authenticationInfo);
    }
}
