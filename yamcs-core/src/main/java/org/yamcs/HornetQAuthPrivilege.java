package org.yamcs;

import java.util.Hashtable;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.hornetq.api.core.client.ClientMessage;

import com.sun.security.auth.UserPrincipal;

/**
 * Messaging authz is checked per message, not per connection, so this object
 * will only supply valid checks when an instance is requested with either a
 * message or a username.
 *
 * Getting an instance without specifying a message or username will return
 * the default Privilege which will have no roles.
 *
 * Getting an instance by specifying a message or username will not
 * perform any authentication of the user. Separate authentication is
 * supported, but not cached. If messages are received by internal services,
 * the connection (and user) has already been validated by the HornetQ
 * mechanisms.
 *
 * Authentication uses a separate LDAP bind with supplied credentials so no
 * additional LDAP privileges are required for the service to function.
 *
 */
public class HornetQAuthPrivilege extends Privilege {
	private String username;

	private HornetQAuthPrivilege( String username ) throws ConfigurationException {
		super();
		this.username = username;
	}

	public static synchronized Privilege getInstance( String username ) {
		try {
			return new HornetQAuthPrivilege( username );
		} catch (ConfigurationException e) {
			System.err.println("Could not create privileges: " + e);
			System.exit(-1);
		}
		return null;
	}
	
	public static synchronized Privilege getInstance( ClientMessage message ) {
		return getInstance( message.getStringProperty( HornetQAuthInterceptor.USERNAME_PROPERTY ) );
	}
	
	public static synchronized Privilege getInstance( String username, String password ) {
		if( authenticated( username, password ) ) {
			return getInstance( username );
		}
		return getInstance();
	}
	
	public static synchronized boolean authenticated( String username, String password ) {
		if( ! Privilege.usePrivileges ) {
			return true;
		}
		if( username == null ) {
			return false;
		}
		DirContext ctx = null;
		try {
			String userDn = "uid="+username+","+userPath;
			Hashtable<String, String> localContextEnv = new Hashtable<String, String>();
			localContextEnv.put(Context.INITIAL_CONTEXT_FACTORY, contextEnv.get(Context.INITIAL_CONTEXT_FACTORY));
			localContextEnv.put(Context.PROVIDER_URL, contextEnv.get(Context.PROVIDER_URL));
			localContextEnv.put("com.sun.jndi.ldap.connect.pool", "true");
			localContextEnv.put(Context.SECURITY_AUTHENTICATION, "simple");
			localContextEnv.put(Context.SECURITY_PRINCIPAL, userDn);
			if( password != null ) {
				localContextEnv.put(Context.SECURITY_CREDENTIALS, password);
			}
			
			ctx = new InitialDirContext( localContextEnv );
			ctx.close();
		} catch (AuthenticationException e) {
			log.warn( "User '{}' not authenticated with LDAP; Could not bind with supplied username and password.", username );
			return false;
		} catch (NamingException e) {
			log.warn( "User '{}' not authenticated with LDAP; An LDAP error was caught: {}", username, e );
			return false;
		}
		return true;
	}
	
	@Override
	public String getCurrentUser() {
		return username;
	}
}
