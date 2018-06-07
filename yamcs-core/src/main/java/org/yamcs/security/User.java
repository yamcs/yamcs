package org.yamcs.security;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by msc on 05/05/15.
 */
public class User {

    private String username;

    private AuthenticationInfo authenticationInfo;

    // A superuser does not require privilege checking
    private boolean superuser = false;

    private Set<String> roles = new HashSet<>();
    private Set<String> tmParaPrivileges = new HashSet<>();
    private Set<String> tmParaSetPrivileges = new HashSet<>();
    private Set<String> tmPacketPrivileges = new HashSet<>();
    private Set<String> tcPrivileges = new HashSet<>();
    private Set<String> streamPrivileges = new HashSet<>();
    private Set<String> cmdHistoryPrivileges = new HashSet<>();
    private Set<String> systemPrivileges = new HashSet<>();

    public User(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;
        this.username = authenticationInfo.getPrincipal();
    }

    public User(String username) {
        this.username = username;
    }

    public AuthenticationInfo getAuthenticationInfo() {
        return authenticationInfo;
    }

    public boolean isSuperuser() {
        return superuser;
    }

    public void setSuperuser(boolean superuser) {
        this.superuser = superuser;
    }

    public void addRole(String role) {
        roles.add(role);
    }

    public Set<String> getTmParaPrivileges() {
        return tmParaPrivileges;
    }

    public Set<String> getTmParaSetPrivileges() {
        return tmParaSetPrivileges;
    }

    public Set<String> getTmPacketPrivileges() {
        return tmPacketPrivileges;
    }

    public Set<String> getTcPrivileges() {
        return tcPrivileges;
    }

    public Set<String> getStreamPrivileges() {
        return streamPrivileges;
    }

    public Set<String> getCmdHistoryPrivileges() {
        return cmdHistoryPrivileges;
    }

    public Set<String> getSystemPrivileges() {
        return systemPrivileges;
    }

    public void addTmParaPrivilege(String privilege) {
        tmParaPrivileges.add(privilege);
    }

    public void addTmParaSetPrivilege(String privilege) {
        tmParaSetPrivileges.add(privilege);
    }

    public void addTmPacketPrivilege(String privilege) {
        tmPacketPrivileges.add(privilege);
    }

    public void addTcPrivilege(String privilege) {
        tcPrivileges.add(privilege);
    }

    public void addStreamPrivilege(String privilege) {
        streamPrivileges.add(privilege);
    }

    public void addCmdHistoryPrivilege(String privilege) {
        cmdHistoryPrivileges.add(privilege);
    }

    public void addSystemPrivilege(String privilege) {
        systemPrivileges.add(privilege);
    }

    public String getUsername() {
        return username;
    }

    /**
     * @return the roles of the calling user
     */
    public String[] getRoles() {
        if (roles == null) {
            return new String[0];
        }
        return roles.toArray(new String[roles.size()]);
    }

    public boolean hasPrivilege(PrivilegeType type, String privilege) {
        if (superuser) {
            return true;
        }
        Set<String> priv = null;
        switch (type) {
        case TM_PARAMETER:
            priv = this.tmParaPrivileges;
            break;
        case TM_PARAMETER_SET:
            priv = this.tmParaSetPrivileges;
            break;
        case TC:
            priv = this.tcPrivileges;
            break;
        case TM_PACKET:
            priv = this.tmPacketPrivileges;
            break;
        case SYSTEM:
            priv = this.systemPrivileges;
            break;
        case STREAM:
            priv = this.streamPrivileges;
            break;
        case CMD_HISTORY:
            priv = this.cmdHistoryPrivileges;
            break;
        }
        if (priv == null) {
            return false;
        }
        for (String p : priv) {
            if (privilege.matches(p)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return username;
    }
}
