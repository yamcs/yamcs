package org.yamcs.artemis;

import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.security.Privilege;

import com.google.common.util.concurrent.AbstractService;

/**
 * Server wide service that initialises and starts the artemis/hornetq server
 *  
 * 
 * @author nm
 *
 */
public class ArtemisServer extends AbstractService {
    static Logger log = LoggerFactory.getLogger(ArtemisServer.class.getName());
    static Logger staticlog = LoggerFactory.getLogger(ArtemisServer.class);
    

    EmbeddedActiveMQ artemisServer;
    
    public ArtemisServer() throws ConfigurationException {
        if(artemisServer!=null) {
            throw new ConfigurationException("This service cannot be instantiated more than once");
        }
    }

    public static EmbeddedActiveMQ setupArtemis() throws Exception {
        //divert artemis logging
        System.setProperty("org.jboss.logging.provider", "slf4j");

        // load optional configuration file name for ActiveMQ Artemis,
        // otherwise default will be artemis.xml
        String artemisConfigFile = "artemis.xml";
        YConfiguration c = YConfiguration.getConfiguration("yamcs");
        if(c.containsKey("artemisConfigFile")) {
            artemisConfigFile = c.getString("artemisConfigFile");    
        }

        EmbeddedActiveMQ artemisServer = new EmbeddedActiveMQ();
        Privilege priv = Privilege.getInstance();
        if (priv.isEnabled()) {
            ActiveMQSecurityManager secmgr = priv.getArtemisAuthModule();
            
            if(secmgr==null) throw new ConfigurationException("Privileges are enabled but there is no artemisAuthModule configured in privileges.yaml");
            artemisServer.setSecurityManager( secmgr);
        }
        if(artemisConfigFile != null) {
            artemisServer.setConfigResourcePath(artemisConfigFile);
        }
        artemisServer.start();
        
        return artemisServer;
    }

    @Override
    protected void doStart() {

        try {
            this.artemisServer = ArtemisServer.setupArtemis();
            notifyStarted();

        }
        catch (Exception e) {
            notifyFailed(e);
        }
    }

    @Override
    protected void doStop() {
        try {
            artemisServer.stop();
            
            notifyStopped();
        } catch (Exception e) {
            log.error("Failed to close the yamcs session",e);
        }
    }
}
