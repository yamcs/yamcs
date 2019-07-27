package org.yamcs.artemis;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.security.SecurityStore;
import org.yamcs.utils.YObjectLoader;

/**
 * Server wide service that initialises and starts the artemis/hornetq server
 * 
 * 
 * @author nm
 *
 */
public class ArtemisServer extends AbstractYamcsService {

    private static EmbeddedActiveMQ broker;

    private ActiveMQSecurityManager securityManager;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("securityManager", OptionType.ANY);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        super.init(yamcsInstance, config);

        // Divert artemis logging
        System.setProperty("org.jboss.logging.provider", "slf4j");
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.Jdk14Logger");

        if (config.containsKey("securityManager")) {
            try {
                securityManager = YObjectLoader.loadObject(config.getMap("securityManager"));
            } catch (IOException e) {
                throw new InitException(e);
            }
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

        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        EmbeddedActiveMQ artemisServer = new EmbeddedActiveMQ();

        if (securityManager != null) {
            artemisServer.setSecurityManager(securityManager);
        } else if (securityStore.isAuthenticationEnabled()) {
            log.warn("Artemis security is unconfigured. All connections are given full permissions");
        } else {
            log.debug("Artemis security is unconfigured. All connections are given full permissions");
        }

        // We are supposed to pass a "classpath resource", however the called code also accepts
        // any string that can be used to construct a java.net.URL.
        Path configDirectory = YamcsServer.getServer().getConfigDirectory();
        Path configFile = configDirectory.resolve("artemis.xml").toAbsolutePath();
        String configResource = configFile.toUri().toURL().toString();
        artemisServer.setConfigResourcePath(configResource);

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
