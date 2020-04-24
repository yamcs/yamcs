package org.yamcs.tctm.ccsds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.SystemParametersCollector;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.tctm.AbstractLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.Link;
import org.yamcs.tctm.TcDataLink;
import org.yamcs.tctm.ccsds.MasterChannelFrameMultiplexer;

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
 * 
 * @author nm
 *
 */
public abstract class AbstractTcFrameLink extends AbstractLink
        implements AggregatedDataLink, TcDataLink, SystemParametersProducer {
    protected int frameCount;
    boolean sendCltu;
    protected MasterChannelFrameMultiplexer multiplexer;
    List<Link> subLinks;

    protected CommandHistoryPublisher commandHistoryPublisher;
    protected TimeService timeService;
    protected SystemParametersCollector sysParamCollector;
    private String sv_linkStatus_id, sp_dataCount_id;
    protected CltuGenerator cltuGenerator;
    final static String CLTU_START_SEQ_KEY = "cltuStartSequence";
    final static String CLTU_TAIL_SEQ_KEY = "cltuTailSequence";

    public AbstractTcFrameLink(String yamcsInstance, String name, YConfiguration config) {
        super(yamcsInstance, name, config);
        this.timeService = YamcsServer.getTimeService(yamcsInstance);

        String cltuEncoding = config.getString("cltuEncoding", null);
        if (cltuEncoding != null) {
            if ("BCH".equals(cltuEncoding)) {
                byte[] startSeq = config.getBinary(CLTU_START_SEQ_KEY, BchCltuGenerator.CCSDS_START_SEQ);
                byte[] tailSeq = config.getBinary(CLTU_TAIL_SEQ_KEY, BchCltuGenerator.CCSDS_TAIL_SEQ);
                boolean randomize = config.getBoolean("randomizeCltu", false);
                cltuGenerator = new BchCltuGenerator(randomize, startSeq, tailSeq);
            } else if ("LDPC64".equals(cltuEncoding)) {
                byte[] startSeq = config.getBinary(CLTU_START_SEQ_KEY, Ldpc64CltuGenerator.CCSDS_START_SEQ);
                byte[] tailSeq = config.getBinary(CLTU_TAIL_SEQ_KEY, CltuGenerator.EMPTY_SEQ);
                cltuGenerator = new Ldpc64CltuGenerator(startSeq, tailSeq);
            } else if ("LDPC256".equals(cltuEncoding)) {
                byte[] startSeq = config.getBinary(CLTU_START_SEQ_KEY, Ldpc256CltuGenerator.CCSDS_START_SEQ);
                byte[] tailSeq = config.getBinary(CLTU_TAIL_SEQ_KEY, CltuGenerator.EMPTY_SEQ);
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
        return timeService.getMissionTime();
    }

    protected void setupSysVariables() {
        this.sysParamCollector = SystemParametersCollector.getInstance(yamcsInstance);
        if (sysParamCollector != null) {
            sysParamCollector.registerProducer(this);
            sv_linkStatus_id = sysParamCollector.getNamespace() + "/" + linkName + "/linkStatus";
            sp_dataCount_id = sysParamCollector.getNamespace() + "/" + linkName + "/dataCount";
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
    public long getDataInCount() {
        return 0;
    }

    @Override
    public long getDataOutCount() {
        return frameCount;
    }

    @Override
    public void resetCounters() {
        frameCount = 0;
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
        throw new ConfigurationException(
                "This class cannot send command directly, please remove the stream associated to the main link");
    }

    /**
     * Ack the BD frames
     * Note: the AD frames are acknowledged in the when the COP1 ack is received
     * 
     * @param tf
     */
    protected void ackBypassFrame(TcTransferFrame tf) {
        if (tf.getCommands() != null) {
            for (PreparedCommand pc : tf.getCommands()) {
                commandHistoryPublisher.publishAck(pc.getCommandId(), CommandHistoryPublisher.ACK_SENT_CNAME_PREFIX,
                        timeService.getMissionTime(), AckStatus.OK);
            }
        }
    }

    protected void failBypassFrame(TcTransferFrame tf, String reason) {
        if (tf.getCommands() != null) {
            for (PreparedCommand pc : tf.getCommands()) {
                commandHistoryPublisher.publishAck(pc.getCommandId(), CommandHistoryPublisher.ACK_SENT_CNAME_PREFIX,
                        TimeEncoding.getWallclockTime(), AckStatus.NOK, reason);

                commandHistoryPublisher.commandFailed(pc.getCommandId(), timeService.getMissionTime(), reason);
            }
        }
    }

}
