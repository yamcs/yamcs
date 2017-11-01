package org.yamcs.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSessionMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionCloseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caches the username from an incoming CreateSessionMessage against its
 * connection, and injects the username into all subsequent Messages from the
 * connection until a SessionCloseMessage is sent by the client.
 * 
 * When a CreateSessionMessage or a SessionCloseMessage is received, the cache
 * is also flushed to remove all connections which have a disconnected status.
 * 
 * @author atu
 *
 */
public class ArtemisAuthInterceptor implements Interceptor {
	public static final String USERNAME_PROPERTY = "username";
	static Logger log = LoggerFactory.getLogger("org.yamcs.security.ActiveMQAuthInterceptor");
	private Map<Object, AuthInjectionAssociation> cache = Collections.synchronizedMap( new HashMap<Object, AuthInjectionAssociation>() );
	
	@Override
	public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
		Object id = connection.getID();
		AuthInjectionAssociation assoc = cache.get( id );
		
		if( assoc != null ) {
			// Unregister
			if ( packet instanceof SessionCloseMessage ) {
				// Unregister connection
				flush( id );
				flushDisconnected();
				log.debug( "Total ### AuthInjectionAssociations {}", cache.size() );
			} else {
				// Inject cached username
				if( packet instanceof SessionSendMessage ) {
					Message m = ((SessionSendMessage)packet).getMessage();
					m.putStringProperty(USERNAME_PROPERTY, assoc.getUsername());
				}
			}
		} else {
			if( packet instanceof CreateSessionMessage ) {
				// Register connection
				assoc = cache( ((CreateSessionMessage)packet).getUsername(), connection );
				flushDisconnected();
				log.debug( "Total ### AuthInjectionAssociations {}", cache.size() );
			}
		}
		// Keep processing
		return true;
	}
	
	/**
	 * Adds the user to the cache so all subsequent Messages have the username
	 * injected.
	 * 
	 * @param username
	 * @param conn
	 * @return
	 */
	public AuthInjectionAssociation cache( String username, RemotingConnection conn ) {
		AuthInjectionAssociation assoc = new AuthInjectionAssociation( username, conn );
		cache.put( conn.getID(), assoc );
		log.debug( "Start +++ {}", assoc );
		return assoc;
	}
	
	/**
	 * Stops username injection for the specified connection.
	 * 
	 * Note a single client may make many connections.
	 * 
	 * @param connectionId
	 */
	public void flush( Object connectionId ) {
		AuthInjectionAssociation assoc = cache.get(connectionId);
		log.debug( "Stop --- {}", assoc );
		synchronized (cache) {
			cache.remove(connectionId);
		}
	}
	
	/**
	 * Stops username injection for all connections made with the specified
	 * username.
	 * 
	 * Note a single username may be shared across many connections.
	 * 
	 * @param username
	 */
	public void flush( String username ) {
		ArrayList<Object> ownedConnections = new ArrayList<Object>();
		synchronized (cache) {
			for( Object connectionId : cache.keySet() ) {
				if( username.equals( cache.get( connectionId ).getUsername() ) ) {
					ownedConnections.add( connectionId );
				}
			}
		}
		for( Object key : ownedConnections ) {
			flush( key );
		}
	}
	
	/**
	 * Stops username injection for all connections which have been destroyed.
	 */
	public void flushDisconnected() {
		ArrayList<Object> destroyedConnections = new ArrayList<Object>();
		RemotingConnection conn = null;
		synchronized (cache) {
			for( Object connectionId : cache.keySet() ) {
				conn = cache.get(connectionId).getConnection();
				if( conn != null && conn.isDestroyed() ) {
					destroyedConnections.add( connectionId );
				}
			}
		}
		for( Object key : destroyedConnections ) {
			flush( key );
		}
	}
	
	public Map<Object, AuthInjectionAssociation> getCache() {
		return cache;
	}
}

/**
 * Used by {@link ArtemisAuthInterceptor} to associate a connection with a username.
 * 
 * @author atu
 *
 */
class AuthInjectionAssociation {
	private String username;
	private RemotingConnection connection;
	public AuthInjectionAssociation( String username, RemotingConnection connection ) {
		this.username = username;
		this.connection = connection;
	}
	public String getUsername() {
		return username;
	}
	public RemotingConnection getConnection() {
		return connection;
	}
	@Override
	public String toString() {
		return "AuthInjectionAssociation [connection "+connection.getID()
				+" (from "+connection.getRemoteAddress()
				+") with username '"+username+"']";
	}
}