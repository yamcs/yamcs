package org.yamcs.tctm.ccsds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersCollector;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.Link;
import org.yamcs.tctm.TcDataLink;
import org.yamcs.tctm.ccsds.MasterChannelFrameMultiplexer;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

import org.yamcs.tctm.ccsds.VcUplinkHandler;
import org.yamcs.tctm.ccsds.error.BchCltuGenerator;
import org.yamcs.tctm.ccsds.error.CltuGenerator;
import org.yamcs.tctm.ccsds.error.Ldpc256CltuGenerator;
import org.yamcs.tctm.ccsds.error.Ldpc64CltuGenerator;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;

/**
 * Sends TC as TC frames (CCSDS 232.0-B-3) or TC frames embedded in CLTU (CCSDS 231.0-B-3). 
 * 
 * @author nm
 *
 */
public abstract class AbstractTcFrameLink extends AbstractExecutionThreadService implements AggregatedDataLink, TcDataLink, SystemParametersProducer {
    volatile boolean enabled = true;
    protected int frameCount;
    boolean sendCltu;
    protected MasterChannelFrameMultiplexer multiplexer;
    YConfiguration config; 
    List<Link> subLinks;
    protected volatile boolean disabled = false;
    protected String name;//link name
    
    protected String yamcsInstance;
    protected CommandHistoryPublisher commandHistoryPublisher;
    protected TimeService timeService;
    protected SystemParametersCollector sysParamCollector;
    private String sv_linkStatus_id, sp_dataCount_id;
    protected Log log;
    protected CltuGenerator cltuGenerator;
    
    public AbstractTcFrameLink(String yamcsInstance, String name, YConfiguration config) {
        this.config = config;
        this.name = name;
        this.yamcsInstance  = yamcsInstance;
        this.timeService = YamcsServer.getTimeService(yamcsInstance);
        this.log = new Log(getClass(), yamcsInstance);
        
        String cltuEncoding = config.getString("cltuEncoding", null);
        if (cltuEncoding != null) {
            if ("BCH".equals(cltuEncoding)) {
                byte[] startSeq = config.getBinary("startSequence", BchCltuGenerator.CCSDS_START_SEQ);
                byte[] tailSeq = config.getBinary("tailSequence", BchCltuGenerator.CCSDS_TAIL_SEQ);
                boolean randomize = config.getBoolean("randomizeCltu", false);
                cltuGenerator = new BchCltuGenerator(randomize, startSeq, tailSeq);
            } else if ("LDPC64".equals(cltuEncoding)) {
                byte[] startSeq = config.getBinary("startSequence", Ldpc64CltuGenerator.CCSDS_START_SEQ);
                byte[] tailSeq = config.getBinary("tailSequence", CltuGenerator.EMPTY_SEQ);
                cltuGenerator = new Ldpc64CltuGenerator(startSeq, tailSeq);
            } else if ("LDPC256".equals(cltuEncoding)) {
                byte[] startSeq = config.getBinary("startSequence", Ldpc256CltuGenerator.CCSDS_START_SEQ);
                byte[] tailSeq = config.getBinary("tailSequence", CltuGenerator.EMPTY_SEQ);
                cltuGenerator = new Ldpc256CltuGenerator(startSeq, tailSeq);
            } else {
                throw new ConfigurationException(
                        "Invalid value '" + cltuEncoding + " for cltu. Valid values are BCH, LDPC64 or LDPC256");
            }
        }
        
        multiplexer = new MasterChannelFrameMultiplexer(yamcsInstance, name, config);
        subLinks = new ArrayList<>();
        for (VcUplinkHandler vch : multiplexer.getVcHandlers()) {
            if (vch instanceof Link) {
                Link l = (Link) vch;
                subLinks.add(l);
                l.setParent(this);
            }
        }
    }

    protected long getCurrentTime() {
        if (timeService != null) {
            return timeService.getMissionTime();
        } else {
            return TimeEncoding.getWallclockTime();
        }
    }
    
    @Override
    public void triggerShutdown() {
        multiplexer.quit();
    }

   
    @Override
    public Status getLinkStatus() {
        if (disabled) {
            return Status.DISABLED;
        }
        if (state() == State.FAILED) {
            return Status.FAILED;
        }

        return Status.OK;
    }
    
    protected void setupSysVariables() {
        this.sysParamCollector = SystemParametersCollector.getInstance(yamcsInstance);
        if (sysParamCollector != null) {
            sysParamCollector.registerProducer(this);
            sv_linkStatus_id = sysParamCollector.getNamespace() + "/" + name + "/linkStatus";
            sp_dataCount_id = sysParamCollector.getNamespace() + "/" + name + "/dataCount";
        } 
    }

    @Override
    public List<ParameterValue> getSystemParameters() {
        long time = getCurrentTime();
        ParameterValue linkStatus = SystemParametersCollector.getPV(sv_linkStatus_id, time, getLinkStatus().name());
        ParameterValue dataCount = SystemParametersCollector.getPV(sp_dataCount_id, time, getDataOutCount());
        return Arrays.asList(linkStatus, dataCount);
    }

    @Override
    public void enable() {
        disabled = false;
    }

    @Override
    public void disable() {
        disabled = true;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }


    @Override
    public long getDataInCount() {
        return 0;
    }


    @Override
    public long getDataOutCount() {
        return frameCount;
    }


    @Override
    public void resetCounters() {
        frameCount++;
    }


    @Override
    public String getName() {
        return name;
    }


    @Override
    public YConfiguration getConfig() {
        return config;
    }


    @Override
    public List<Link> getSubLinks() {
        return subLinks;
    }

    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryPublisher) {
        this.commandHistoryPublisher = commandHistoryPublisher;
    }

    @Override
    public void sendTc(PreparedCommand preparedCommand) {
        throw new ConfigurationException("This class cannot send command directly, please remove the stream associated to the main link");
    }

}
