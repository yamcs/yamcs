package org.yamcs.tctm;

import java.util.concurrent.atomic.AtomicBoolean;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.logging.Log;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;

/**
 * Abstract link implementation as a {@link Service} handling the basic enable/disable getConfig operations
 * 
 * @author nm
 *
 */
public abstract class AbstractLink extends AbstractService implements Link {
    protected final String yamcsInstance;
    protected final String name;
    protected final Log log;
    protected final EventProducer eventProducer;
    protected final YConfiguration config;
    protected final AtomicBoolean disabled = new AtomicBoolean(false);

    public AbstractLink(String instance, String name, YConfiguration config) throws ConfigurationException {
        this.yamcsInstance = instance;
        this.name = name;
        this.config = config;
        log = new Log(getClass(), instance);
        log.setContext(name);
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, getClass().getSimpleName(), 10000);
    }

    @Override
    public YConfiguration getConfig() {
        return config;
    }

    @Override
    public String getName() {
        return name;
    }
    
    
    @Override
    public Status getLinkStatus() {
        if (isDisabled()) {
            return Status.DISABLED;
        }
        if (state() == State.FAILED) {
            return Status.FAILED;
        }

        return Status.OK;
    }

    @Override
    public String getDetailedStatus() {
        return "";
    }
    
    /**
     * Sets the disabled to false such that getNextPacket does not ignore the received datagrams
     */
    @Override
    public void enable() {
        boolean b = disabled.getAndSet(false);
        if(b) {
            try {
                doEnable();
            } catch (Exception e) {
                disabled.set(true);
                log.warn("Failed to enable link", e);
            }
        }
    }

    @Override
    public void disable() {
        boolean b = disabled.getAndSet(true);
        if(!b) {
            try {
                doDisable();
            } catch (Exception e) {
                disabled.set(false);
                log.warn("Failed to disable link", e);
            }
        }
    }

    @Override
    public boolean isDisabled() {
        return disabled.get();
    }
    

    protected abstract void doDisable() throws Exception;
    protected abstract void doEnable() throws Exception;

}
