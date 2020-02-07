package org.yamcs.tctm;

import java.util.concurrent.atomic.AtomicBoolean;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.logging.Log;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;

import io.netty.channel.nio.NioEventLoopGroup;

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

    /**
     * singleton for netty worker group. In the future we may have an option to create different worker groups for
     * different links but for now we stick to one.
     */
    static NioEventLoopGroup nelg = new NioEventLoopGroup();

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

        return connectionStatus();
    }

    @Override
    public String getDetailedStatus() {
        return "";
    }

    protected static NioEventLoopGroup getEventLoop() {
        return nelg;
    }

    /**
     * Sets the disabled to false such that getNextPacket does not ignore the received datagrams
     */
    @Override
    public void enable() {
        boolean b = disabled.getAndSet(false);
        if (b) {
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
        if (!b) {
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

    protected void doDisable() throws Exception {};

    protected void doEnable() throws Exception {};
    
    /**
     * In case the link should be connected (i.e. is running and enabled) this method is called to return the actual connection status
     */
    protected abstract Status connectionStatus();

}
