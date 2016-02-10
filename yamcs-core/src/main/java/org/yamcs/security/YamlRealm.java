package org.yamcs.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

import java.util.*;

/**
 * Created by msc on 05/05/15.
 */
public class YamlRealm implements Realm {

    static Logger log = LoggerFactory.getLogger(YamlRealm.class);

    static String configFileName;
    static String tm_parameter_privileges = "tm_parameter_privileges";
    static String tm_parameter_set_privileges = "tm_parameter_set_privileges";
    static String tm_packet_privileges = "tm_packet_privileges";
    static String tc_privileges = "tc_privileges";
    static String system_privileges = "system_privileges";

    static {
        YConfiguration privConf = YConfiguration.getConfiguration("privileges");
        configFileName = privConf.getString("yamlRealmFilename");
        configFileName = configFileName.substring(0, configFileName.length()-5); // remove the .yaml
    }

    @Override
    public boolean supports(AuthenticationToken authenticationToken) {
        // supports only username/password authentication
        return authenticationToken.getClass() == UsernamePasswordToken.class
                || authenticationToken.getClass() == HqClientMessageToken.class;
    }

    @Override
    public boolean authenticates(AuthenticationToken authenticationToken) {
        UsernamePasswordToken usernamePasswordToken = (UsernamePasswordToken)authenticationToken;

        if(usernamePasswordToken == null
                || usernamePasswordToken.getUsername() == null
                || usernamePasswordToken.getPasswordS() == null)
            return false;

        YConfiguration conf = YConfiguration.getConfiguration(configFileName);
        boolean passwordsHash = conf.getBoolean("passwordsHash");

        Map<String, Object> users = conf.getMap("users");
        for(String user: users.keySet())
        {
            List<String> userDef = conf.getList("users", user);
            String password= userDef.get(0);

            boolean userValid = user.equals(usernamePasswordToken.getUsername());
            if(userValid) {
                boolean passwordValid = false;
                if (passwordsHash) {
                    try {
                        passwordValid = PasswordHash.validatePassword(usernamePasswordToken.getPasswordS(), password);
                    } catch (Exception e) {
                        log.error("Unable to validate hashed password, please check format of the hash.", e);
                    }
                } else {
                    passwordValid = password.equals(usernamePasswordToken.getPasswordS());
                }
                return passwordValid;
            }
        }

        return false;
    }

    @Override
    public User loadUser(AuthenticationToken authenticationToken) {
        UsernamePasswordToken usernamePasswordToken = (UsernamePasswordToken)authenticationToken;
        User user = new User(authenticationToken);
        user.lastUpdated = System.currentTimeMillis();

        try {
            YConfiguration conf = YConfiguration.getConfiguration(configFileName, true);
            List<String> userDef = conf.getList("users", usernamePasswordToken.getUsername());

            // Load roles
            Set<String> userRoles = new HashSet<>();
            for (int i = 1; i < userDef.size(); i++) {
                userRoles.add(userDef.get(i));
            }
            user.roles = userRoles;

            // Load Privileges
            user.tmParaPrivileges = new HashSet<>();
            user.tmParaSetPrivileges = new HashSet<>();
            user.tmPacketPrivileges = new HashSet<>();
            user.tcPrivileges = new HashSet<>();
            user.systemPrivileges = new HashSet<>();
            for (String role : userRoles) {
                user.tmParaPrivileges.addAll(getPrivileges(conf, role, tm_parameter_privileges));
                user.tmParaSetPrivileges.addAll(getPrivileges(conf, role, tm_parameter_set_privileges));
                user.tmPacketPrivileges.addAll(getPrivileges(conf, role, tm_packet_privileges));
                user.tcPrivileges.addAll(getPrivileges(conf, role, tc_privileges));
                user.systemPrivileges.addAll(getPrivileges(conf, role, system_privileges));
            }
        } catch (ConfigurationException e)  {
            log.warn("Unable to load user " + usernamePasswordToken + " from YamlRealm: {}", e.getMessage());
        }

        user.setAuthenticated(authenticates(authenticationToken));
        return user;
    }


    private List getPrivileges(YConfiguration conf, String role, String privilegesType) {
        List result = null;
        try {
            result = conf.getList("roles", role, privilegesType);
        } catch (Exception e) {
            log.warn("No privileges of type " + privilegesType + " for role " + role);
            result = new LinkedList<>();
        }
        return result;
    }
}
