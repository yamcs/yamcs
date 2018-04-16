package org.yamcs.security;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

/**
 * Created by msc on 05/05/15.
 */
public class YamlRealm implements Realm {

    private static final Logger log = LoggerFactory.getLogger(YamlRealm.class);
    private static final String TM_PARAMETER_PRIVILEGES = "tm_parameter_privileges";
    private static final String TM_PARAMETER_SET_PRIVILEGES = "tm_parameter_set_privileges";
    private static final String TM_PACKET_PRIVILEGES = "tm_packet_privileges";
    private static final String TC_PRIVILEGES = "tc_privileges";
    private static final String SYSTEM_PRIVILEGES = "system_privileges";
    private static final String STREAM_PRIVILEGES = "stream_privileges";
    private static final String CMD_HISTORY_PRIVILEGES = "cmd_history_privileges";

    private boolean hashedPasswords;

    public YamlRealm() {
        YConfiguration yconf = YConfiguration.getConfiguration("credentials");
        hashedPasswords = yconf.getBoolean("passwordsHash");
    }

    @Override
    public boolean supports(AuthenticationToken authToken) {
        return authToken instanceof UsernamePasswordToken
                || authToken instanceof AccessToken;
    }

    @Override
    public boolean authenticate(AuthenticationToken authToken) {
        if (authToken == null || authToken.getPrincipal() == null) {
            return false;
        }
        if (authToken instanceof UsernamePasswordToken) {
            return authenticateUsernamePasswordToken((UsernamePasswordToken) authToken);
        } else if (authToken instanceof AccessToken) {
            return authenticateAccessToken((AccessToken) authToken);
        }
        return false;
    }

    private boolean authenticateUsernamePasswordToken(UsernamePasswordToken authToken) {
        if (authToken.getPasswordS() == null) {
            return false;
        }

        List<String> userDef = findUserDefinition(authToken.getUsername());
        if (userDef != null) {
            String password = userDef.get(0);
            if (hashedPasswords) {
                try {
                    return PasswordHash.validatePassword(authToken.getPasswordS(), password);
                } catch (Exception e) {
                    log.error("Unable to validate hashed password, please check format of the hash.", e);
                    return false;
                }
            } else {
                return password.equals(authToken.getPasswordS());
            }
        }

        return false;
    }

    private boolean authenticateAccessToken(AccessToken authToken) {
        List<String> userDef = findUserDefinition((String) authToken.getPrincipal());
        return userDef != null && !authToken.isExpired();
    }

    private List<String> findUserDefinition(String username) {
        YConfiguration conf = YConfiguration.getConfiguration("credentials", true);
        try {
            return conf.getList("users", username);
        } catch (ConfigurationException e) {
            return null;
        }
    }

    @Override
    public User loadUser(AuthenticationToken authToken) {
        User user = new User(authToken);

        YConfiguration conf = YConfiguration.getConfiguration("credentials", true);
        List<String> userDef = conf.getList("users", user.getPrincipalName());

        for (int i = 1; i < userDef.size(); i++) {
            String role = userDef.get(i);
            user.addRole(role);
            addRolePrivileges(user, conf, role);
        }

        return user;
    }

    private void addRolePrivileges(User user, YConfiguration conf, String role) {
        Map<String, Object> roleConf = conf.getMap("roles", role);
        if (roleConf.containsKey(TM_PARAMETER_PRIVILEGES)) {
            List<String> privileges = YConfiguration.getList(roleConf, TM_PARAMETER_PRIVILEGES);
            for (String privilege : privileges) {
                user.addTmParaPrivilege(privilege);
            }
        }
        if (roleConf.containsKey(TM_PARAMETER_SET_PRIVILEGES)) {
            List<String> privileges = YConfiguration.getList(roleConf, TM_PARAMETER_SET_PRIVILEGES);
            for (String privilege : privileges) {
                user.addTmParaSetPrivilege(privilege);
            }
        }
        if (roleConf.containsKey(TM_PACKET_PRIVILEGES)) {
            List<String> privileges = YConfiguration.getList(roleConf, TM_PACKET_PRIVILEGES);
            for (String privilege : privileges) {
                user.addTmPacketPrivilege(privilege);
            }
        }
        if (roleConf.containsKey(TC_PRIVILEGES)) {
            List<String> privileges = YConfiguration.getList(roleConf, TC_PRIVILEGES);
            for (String privilege : privileges) {
                user.addTcPrivilege(privilege);
            }
        }
        if (roleConf.containsKey(SYSTEM_PRIVILEGES)) {
            List<String> privileges = YConfiguration.getList(roleConf, SYSTEM_PRIVILEGES);
            for (String privilege : privileges) {
                user.addSystemPrivilege(privilege);
            }
        }
        if (roleConf.containsKey(STREAM_PRIVILEGES)) {
            List<String> privileges = YConfiguration.getList(roleConf, STREAM_PRIVILEGES);
            for (String privilege : privileges) {
                user.addStreamPrivilege(privilege);
            }
        }
        if (roleConf.containsKey(CMD_HISTORY_PRIVILEGES)) {
            List<String> privileges = YConfiguration.getList(roleConf, CMD_HISTORY_PRIVILEGES);
            for (String privilege : privileges) {
                user.addCmdHistoryPrivilege(privilege);
            }
        }
    }
}
