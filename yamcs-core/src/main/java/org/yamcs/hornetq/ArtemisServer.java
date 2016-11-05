package org.yamcs.hornetq;

import static org.yamcs.api.artemis.Protocol.DATA_TO_HEADER_NAME;
import static org.yamcs.api.artemis.Protocol.LINK_CONTROL_ADDRESS;
import static org.yamcs.api.artemis.Protocol.LINK_INFO_ADDRESS;
import static org.yamcs.api.artemis.Protocol.REPLYTO_HEADER_NAME;
import static org.yamcs.api.artemis.Protocol.REQUEST_TYPE_HEADER_NAME;

import java.io.IOException;
import java.io.ObjectOutputStream;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.management.LinkListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.YamcsManagement.LinkInfo;
import org.yamcs.protobuf.YamcsManagement.MissionDatabaseRequest;
import org.yamcs.security.HornetQAuthManager;
import org.yamcs.security.HqClientMessageToken;
import org.yamcs.security.Privilege;
import org.yamcs.utils.ActiveMQBufferOutputStream;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.AbstractService;

/**
 * Server wide service that initialises and starts the artemis/hornetq server
 *  
 * 
 * @author nm
 *
 */
public class ArtemisServer extends AbstractService{
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
        artemisServer.setSecurityManager( new HornetQAuthManager() );
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
