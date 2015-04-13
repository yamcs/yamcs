package org.yamcs.api;


import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.hornetq.core.remoting.impl.netty.TransportConstants;

public class YamcsSession {
    boolean invm=true;
    ClientSessionFactory sessionFactory;
    public ClientSession session;
    private YamcsConnectData ycd;
    ServerLocator locator;
    boolean preAcknowledge=true;

    public static String hornetqInvmUser;
    public static String hornetqInvmPass;
    static {
	hornetqInvmUser = java.util.UUID.randomUUID().toString();
	hornetqInvmPass = java.util.UUID.randomUUID().toString();
    }

    
    static {
	//divert hornetq logging
	System.setProperty("org.jboss.logging.provider", "slf4j");
    }
    
    /* used for debugging not properly closed sessions
    public static List<YamcsSession> sessionList = Collections.synchronizedList(new ArrayList<YamcsSession>());    
    public Exception creatorContext; 
    private YamcsSession() {   
	sessionList.add(this);
	creatorContext = new Exception();
    }
    */
    
    private YamcsSession() {}
    public ClientSessionFactory getSessionFactory() {
	return sessionFactory;
    }

    public ClientSession getSession() {
	return session;
    }

    public ServerLocator getLocator() {
	return locator;
    }

    public YamcsClient.ClientBuilder newClientBuilder() {
	return new YamcsClient.ClientBuilder(this);
    }

    public void close() throws HornetQException {
	try {
	    if (session != null)
		session.close();
	} finally {
	    if (sessionFactory != null)
		sessionFactory.close();
	    if (locator != null)
		locator.close();
	}
	/*sessionList.remove(this);*/
	
    }

    private void init() throws YamcsApiException {
	try {
	    String username = null;
	    String password = null;
	    if(invm) {
		locator = HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(Protocol.IN_VM_FACTORY));
		sessionFactory =  locator.createSessionFactory();
		username = hornetqInvmUser;
		password = hornetqInvmPass;
	    } else {
		if(ycd.host!=null) {
		    Map<String, Object> tcpConfig =new HashMap<String,Object>();
		    tcpConfig.put(TransportConstants.HOST_PROP_NAME, ycd.host);
		    tcpConfig.put(TransportConstants.PORT_PROP_NAME, ycd.port);
		    locator = HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(NettyConnectorFactory.class.getName(),tcpConfig));
		    sessionFactory =  locator.createSessionFactory();
		} else {
		    locator = HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(NettyConnectorFactory.class.getName()));
		    sessionFactory =  locator.createSessionFactory();
		}
		username = ycd.username;
		password = ycd.password;
	    }
	    // All sessions are authenticated, a null username translates to
	    // guest auth and authz (if allowed by server)
	    session = sessionFactory.createSession(username, password, false, true, true, preAcknowledge, 1);
	    session.start();
	} catch( HornetQException e ) {
	    // Pass specific HornetQExceptions as our cause, helps identify
	    // permissions problems
	    try {
		close();
	    } catch (HornetQException e1) {}
	    throw new YamcsApiException( e.getMessage(), e );
	} catch(Exception e) {
	    // Pass Exception's cause as our cause.
	    System.out.println( e );
	    // close everything
	    try {
		close();
	    } catch (HornetQException e1) {} 
	    throw new YamcsApiException(e.getMessage(), e.getCause());
	}
    }

    public static YamcsSession.Builder newBuilder() {
	return new Builder();
    }


    public static class Builder {
	YamcsSession result=new YamcsSession();

	public Builder setConnectionParams(String host, int port) {
	    return setConnectionParams(host, port, null, null);
	}

	public Builder setConnectionParams(String host, int port, String username, String password) {
	    result.invm = false;
	    result.ycd = new YamcsConnectData();
	    result.ycd.host = host;
	    result.ycd.port = port;
	    result.ycd.username = username;
	    result.ycd.password = password;
	    return this;
	}

	public Builder setConnectionParams(String url) throws URISyntaxException {
	    result.ycd = YamcsConnectData.parse(url);
	    result.invm = (result.ycd.host == null);
	    return this;
	}

	public Builder setConnectionParams(YamcsConnectData ycd) {
	    result.invm = (ycd.host == null);
	    result.ycd = ycd;
	    return this;
	}

	/**
	 * If set to true, the consumers created with this session will automatically acknowledge the messages.
	 * The acknowledge happens on the server before the consumers receive the messages. 
	 * @param preack
	 * @return
	 */
	public Builder setPreAcknowledge (boolean preack) {
	    result.preAcknowledge = preack;
	    return this;
	}

	public YamcsSession build() throws YamcsApiException {
	    result.init();
	    return result;
	}
    }

    public static void main(String[] argv) throws Exception {
	@SuppressWarnings("unused")
	YamcsSession ysession=YamcsSession.newBuilder().setConnectionParams("aces-test",5445).build();
    }
}
