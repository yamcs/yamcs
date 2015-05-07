package org.yamcs.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

import javax.naming.Context;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

/**
 * Created by msc on 05/05/15.
 */
public class YamlRealm implements Realm {

    static Logger log = LoggerFactory.getLogger(YamlRealm.class);

    static String configFileName;
    static String tm_parameter_privileges = "tm_parameter_privileges";
    static String tm_packet_privileges = "tm_packet_privileges";
    static String tc_privileges = "tc_privileges";
    static String system_privileges = "system_privileges";

    static {
        try {
            YConfiguration privConf = YConfiguration.getConfiguration("privileges");
            configFileName = privConf.getString("yamlRealmFilename");
            configFileName = configFileName.substring(0, configFileName.length()-5); // remove the .yaml

        } catch (ConfigurationException e) {
            log.error("Failed to load 'ldap' configuration: ", e);
            System.exit(-1);
        }
    }

    @Override
    public boolean supports(AuthenticationToken authenticationToken) {
        // supports only username/password authentication
        return authenticationToken.getClass() == UsernamePasswordToken.class;
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
                }
                else
                {
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
            YConfiguration conf = YConfiguration.getConfiguration(configFileName);
            List<String> userDef = conf.getList("users", usernamePasswordToken.getUsername());

            // Load roles
            Set<String> userRoles = new HashSet<>();
            for (int i = 1; i < userDef.size(); i++) {
                userRoles.add(userDef.get(i));
            }
            user.roles = userRoles;

            // Load Priviledges
            user.tmParaPrivileges = new HashSet<>();
            user.tmPacketPrivileges = new HashSet<>();
            user.tcPrivileges = new HashSet<>();
            user.systemPrivileges = new HashSet<>();
            for (String role : userRoles) {
                user.tmParaPrivileges.addAll(conf.getList("roles", role, tm_parameter_privileges));
                user.tmPacketPrivileges.addAll(conf.getList("roles", role, tm_packet_privileges));
                user.tcPrivileges.addAll(conf.getList("roles", role, tc_privileges));
                user.systemPrivileges.addAll(conf.getList("roles", role, system_privileges));
            }
        }
        catch (Exception e)
        {
            log.error("Unable to load user " + usernamePasswordToken + " from YamlRealm", e);
        }

        user.setAuthenticated(authenticates(authenticationToken));
        return user;
    }
}
