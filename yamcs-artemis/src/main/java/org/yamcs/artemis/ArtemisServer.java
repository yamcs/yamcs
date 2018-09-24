package org.yamcs.artemis;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsService;
import org.yamcs.security.SecurityStore;
import org.yamcs.utils.YObjectLoader;

import com.google.common.util.concurrent.AbstractService;

/**
 * Server wide service that initialises and starts the artemis/hornetq server
 * 
 * 
 * @author nm
 *
 */
public class ArtemisServer extends AbstractService implements YamcsService {

    private static Logger log = LoggerFactory.getLogger(ArtemisServer.class.getName());

    private static EmbeddedActiveMQ broker;

    private String configFile; // Must be on classpath (note that etc folder is usually added to Yamcs classpath)
    private ActiveMQSecurityManager securityManager;

    public ArtemisServer() throws IOException {
        this(Collections.emptyMap());
    }

    public ArtemisServer(Map<String, Object> args) throws IOException {
        // Divert artemis logging
        System.setProperty("org.jboss.logging.provider", "slf4j");
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.Jdk14Logger");

        configFile = YConfiguration.getString(args, "configFile", "artemis.xml");

        YConfiguration yconf = YConfiguration.getConfiguration("yamcs");
        if (yconf.containsKey("artemisConfigFile")) {
            log.warn("Deprecation: migrate 'artemisConfigFile' setting to arg 'configFile' of ArtemisServer");
            configFile = yconf.getString("artemisConfigFile");
        }

        if (args.containsKey("securityManager")) {
            securityManager = YObjectLoader.loadObject(YConfiguration.getMap(args, "securityManager"));
        }
    }

    @Override
    protected void doStart() {
        try {
            broker = startEmbeddedBroker();
            notifyStarted();
        } catch (Exception e) {
            notifyFailed(e);
        }
    }

    public EmbeddedActiveMQ startEmbeddedBroker() throws Exception {
        if (broker != null) {
            throw new UnsupportedOperationException("This service cannot be instantiated more than once");
        }

        EmbeddedActiveMQ artemisServer = new EmbeddedActiveMQ();

        if (securityManager != null) {
            artemisServer.setSecurityManager(securityManager);
        } else if (SecurityStore.getInstance().isEnabled()) {
            log.warn("Artemis security is unconfigured. All connections are given full permissions");
        } else {
            log.debug("Artemis security is unconfigured. All connections are given full permissions");
        }

        artemisServer.setConfigResourcePath(configFile);
        artemisServer.start();

        return artemisServer;
    }

    @Override
    protected void doStop() {
        try {
            broker.stop();
            notifyStopped();
        } catch (Exception e) {
            log.error("Failed to close Yamcs broker session", e);
        }
    }
}
