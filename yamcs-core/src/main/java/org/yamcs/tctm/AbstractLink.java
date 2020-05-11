package org.yamcs.tctm;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersCollector;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.time.TimeService;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;

import io.netty.channel.nio.NioEventLoopGroup;

/**
 * Abstract link implementation as a {@link Service} handling the basic enable/disable getConfig operations
 * 
 * @author nm
 *
 */
public abstract class AbstractLink extends AbstractService implements Link, SystemParametersProducer {
    protected String yamcsInstance;
    protected String linkName;
    protected Log log;
    protected EventProducer eventProducer;
    protected YConfiguration config;
    protected AtomicBoolean disabled = new AtomicBoolean(false);
    private String sv_linkStatus_id, sp_dataOutCount_id, sp_dataInCount_id;
    protected TimeService timeService;

    /**
     * singleton for netty worker group. In the future we may have an option to create different worker groups for
     * different links but for now we stick to one.
     */
    static NioEventLoopGroup nelg = new NioEventLoopGroup();

    @Override
    public void init(String instance, String name, YConfiguration config) throws ConfigurationException {
        this.yamcsInstance = instance;
        this.linkName = name;
        this.config = config;
        log = new Log(getClass(), instance);
        log.setContext(name);
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, getClass().getSimpleName(), 10000);
        this.timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    @Override
    public YConfiguration getConfig() {
        return config;
    }

    @Override
    public String getName() {
        return linkName;
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

    public boolean isRunningAndEnabled() {
        State state = state();
        return (state == State.RUNNING || state == State.STARTING) && !disabled.get();
    }

    protected void doDisable() throws Exception {
    };

    protected void doEnable() throws Exception {
    };

    /**
     * In case the link should be connected (i.e. is running and enabled) this method is called to return the actual
     * connection status
     */
    protected abstract Status connectionStatus();

    protected long getCurrentTime() {
        return timeService.getMissionTime();
    }

    @Override
    public void setupSystemParameters(SystemParametersCollector sysParamCollector) {
        sv_linkStatus_id = sysParamCollector.getNamespace() + "/" + linkName + "/linkStatus";
        sp_dataOutCount_id = sysParamCollector.getNamespace() + "/" + linkName + "/dataOutCount";
        sp_dataInCount_id = sysParamCollector.getNamespace() + "/" + linkName + "/dataInCount";
    }

    @Override
    public List<ParameterValue> getSystemParameters() {
        long time = getCurrentTime();

        ArrayList<ParameterValue> list = new ArrayList<>();
        try {
            collectSystemParameters(time, list);
        } catch (Exception e) {
            log.error("Exception caught when collecting link system parameters", e);
        }
        return list;
    }

    /**
     * adds system parameters link status and data in/out to the list.
     * <p>
     * The inheriting classes should call super.collectSystemParameters and then add their own parameters to the list
     * 
     * @param time
     * @param list
     */
    protected void collectSystemParameters(long time, List<ParameterValue> list) {
        list.add(SystemParametersCollector.getPV(sv_linkStatus_id, time, getLinkStatus().name()));
        list.add(SystemParametersCollector.getPV(sp_dataOutCount_id, time, getDataOutCount()));
        list.add(SystemParametersCollector.getPV(sp_dataInCount_id, time, getDataInCount()));
    }

}
