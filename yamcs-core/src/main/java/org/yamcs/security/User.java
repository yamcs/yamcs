package org.yamcs.security;

import java.util.Date;
import java.util.Set;

/**
 * Created by msc on 05/05/15.
 */
public class User {

	AuthenticationToken authenticationToken;
	long lastUpdated;

	boolean authenticated = false;
	boolean rolesAndPriviledgesLoaded = false;

	Set<String> roles;
	Set<String> tmParaPrivileges;
    Set<String> tmParaSetPrivileges;
	Set<String> tmPacketPrivileges;
	Set<String> tcPrivileges;
	Set<String> systemPrivileges;

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

	public Set<String> getSystemPrivileges() {
		return systemPrivileges;
	}

	/**
	 * Constructor
	 * @param authenticationToken
	 */
	public User(AuthenticationToken authenticationToken)
	{
		this.authenticationToken = authenticationToken;
	}

	/**
	 * Getters
	 */
	public AuthenticationToken getAuthenticationToken() {
		return authenticationToken;
	}
	public String getPrincipalName()
	{
		Object principal = authenticationToken.getPrincipal();
		return principal != null? principal.toString() : null;
	}


	/**
	 *
	 * @return the roles of the calling user
	 */
	public String[] getRoles() {
		if(roles == null)
			return new String[0];
		return roles.toArray(new String[roles.size()]);
	}

	public boolean hasRole(String role ) {
		if(this.roles==null) return false;
		return (this.roles.contains( role ) );
	}


	public boolean hasPrivilege(Privilege.Type type, String privilege) {
		Set<String> priv = null;
        if(privilege == null)
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
		}
		if (priv == null)
			return false;
		for (String p : priv) {
			if (privilege.matches(p))
				return true;
		}
		return false;
	}





	@Override
    public String toString() {
		return "User:" + authenticationToken.getPrincipal().toString()
				+ "\n authenticated: " + authenticated
				+ "\n roles: " + roles + "\n   tm parameter privileges:"
                + tmParaPrivileges + "\n   tm parameter set privileges:"
                + tmParaSetPrivileges + "\n   tm packet privileges:"
				+ tmPacketPrivileges + "\n   tc privileges:" + tcPrivileges
				+ "\n   system privileges:" + systemPrivileges
				+ "\n   lastUpdated:" + new Date(lastUpdated);
	}

	public boolean isAuthenticated() {
		return authenticated;
	}

	public void setAuthenticated(boolean authenticated) {
		this.authenticated = authenticated;
	}
}
