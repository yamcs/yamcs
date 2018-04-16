package org.yamcs.security;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by msc on 05/05/15.
 */
public class User {

    private AuthenticationToken authToken;
    private Date lastUpdated;

    private Set<String> roles = new HashSet<>();
    private Set<String> tmParaPrivileges = new HashSet<>();
    private Set<String> tmParaSetPrivileges = new HashSet<>();
    private Set<String> tmPacketPrivileges = new HashSet<>();
    private Set<String> tcPrivileges = new HashSet<>();
    private Set<String> streamPrivileges = new HashSet<>();
    private Set<String> cmdHistoryPrivileges = new HashSet<>();
    private Set<String> systemPrivileges = new HashSet<>();

    public User(AuthenticationToken authToken) {
        this.authToken = authToken;
        lastUpdated = new Date();
    }

    public Date getLastUpdated() {
        return lastUpdated;
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

    public AuthenticationToken getAuthenticationToken() {
        return authToken;
    }

    public String getPrincipalName() {
        Object principal = authToken.getPrincipal();
        return principal != null ? principal.toString() : null;
    }

    /**
     * @return the roles of the calling user
     */
    public String[] getRoles() {
        if (roles == null)
            return new String[0];
        return roles.toArray(new String[roles.size()]);
    }

    public boolean hasRole(String role) {
        if (this.roles == null)
            return false;
        return (this.roles.contains(role));
    }

    public boolean hasPrivilege(PrivilegeType type, String privilege) {
        Set<String> priv = null;
        if (privilege == null)
            return true;
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
        return "User: " + authToken.getPrincipal().toString()
                + "\n roles: " + roles
                + "\n   tm parameter privileges:" + tmParaPrivileges
                + "\n   tm parameter set privileges:" + tmParaSetPrivileges
                + "\n   tm packet privileges:" + tmPacketPrivileges
                + "\n   tc privileges:" + tcPrivileges
                + "\n   system privileges:" + systemPrivileges
                + "\n   lastUpdated:" + lastUpdated;
    }
}
