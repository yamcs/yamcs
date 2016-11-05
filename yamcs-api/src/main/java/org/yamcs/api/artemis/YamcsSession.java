package org.yamcs.api.artemis;


import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.UsernamePasswordToken;

public class YamcsSession {
    boolean invm=true;
    ClientSessionFactory sessionFactory;
    public ClientSession session;
    private YamcsConnectionProperties ycd;
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

    public void close() throws ActiveMQException {
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
                locator = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(Protocol.IN_VM_FACTORY));
                sessionFactory =  locator.createSessionFactory();
                username = hornetqInvmUser;
                password = hornetqInvmPass;
            } else {
                if(ycd.getHost()!=null) {
                    Map<String, Object> tcpConfig =new HashMap<String,Object>();
                    tcpConfig.put(TransportConstants.HOST_PROP_NAME, ycd.getHost());
                    tcpConfig.put(TransportConstants.PORT_PROP_NAME, ycd.getPort());
                    locator = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(NettyConnectorFactory.class.getName(),tcpConfig));
                    sessionFactory =  locator.createSessionFactory();
                } else {
                    locator = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(NettyConnectorFactory.class.getName()));
                    sessionFactory =  locator.createSessionFactory();
                }
                AuthenticationToken authToken = ycd.getAuthenticationToken();
                if(authToken instanceof UsernamePasswordToken) {
                    UsernamePasswordToken upt = (UsernamePasswordToken)authToken;
                    username = upt.getUsername();
                    password = upt.getPasswordS();
                } else {
                    throw new RuntimeException("Authentication token of type "+authToken.getClass()+" not supported for the Aremis connections");
                }

            }
            // All sessions are authenticated, a null username translates to
            // guest auth and authz (if allowed by server)
            session = sessionFactory.createSession(username, password, false, true, true, preAcknowledge, 1);
            session.start();
        } catch( ActiveMQException e ) {
            // Pass specific ActiveMQExceptions as our cause, helps identify
            // permissions problems
            try {
                close();
            } catch (ActiveMQException e1) {}
            throw new YamcsApiException( e.getMessage(), e );
        } catch(Exception e) {
            // Pass Exception's cause as our cause.
            System.out.println( e );
            // close everything
            try {
                close();
            } catch (ActiveMQException e1) {} 
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
            result.ycd = new YamcsConnectionProperties();
            result.ycd.setHost(host);
            result.ycd.setPort(port);
            result.ycd.setAuthenticationToken(new UsernamePasswordToken(username, password));
            return this;
        }

        public Builder setConnectionParams(String url) throws URISyntaxException {
            result.ycd = YamcsConnectionProperties.parse(url);
            result.invm = (result.ycd.getHost() == null);
            return this;
        }

        public Builder setConnectionParams(YamcsConnectionProperties ycd) {
            result.invm = (ycd.getHost() == null);
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
    public ClientMessage createMessage(boolean durable) {
        return session.createMessage(durable);
    }

    @Override
    protected void finalize() {
        if(sessionFactory!=null) sessionFactory.close();
        if(locator!=null) locator.close();
    }

    public static void main(String[] argv) throws Exception {
        @SuppressWarnings("unused")
        YamcsSession ysession=YamcsSession.newBuilder().setConnectionParams("aces-test",5445).build();
    }


}
