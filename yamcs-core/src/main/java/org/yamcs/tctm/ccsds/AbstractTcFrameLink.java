package org.yamcs.tctm.ccsds;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.AbstractLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.Link;
import org.yamcs.tctm.TcDataLink;
import org.yamcs.tctm.ccsds.TcManagedParameters.PriorityScheme;
import org.yamcs.tctm.ccsds.TransferFrameDecoder.CcsdsFrameType;
import org.yamcs.tctm.ccsds.error.BchCltuGenerator;
import org.yamcs.tctm.ccsds.error.CltuGenerator;
import org.yamcs.tctm.ccsds.error.Ldpc256CltuGenerator;
import org.yamcs.tctm.ccsds.error.Ldpc64CltuGenerator;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.YObjectLoader;

/**
 * Sends TC as TC frames (CCSDS 232.0-B-3) or TC frames embedded in CLTU (CCSDS 231.0-B-3).
 * 
 */
public abstract class AbstractTcFrameLink extends AbstractLink implements AggregatedDataLink, TcDataLink {
    protected int frameCount;
    boolean sendCltu;
    protected MasterChannelFrameMultiplexer multiplexer;
    List<Link> subLinks;
    boolean randomize;

    // do not randomize the virtual channels from this array
    IntArray skipRandomizationForVcs = null;

    protected CommandHistoryPublisher commandHistoryPublisher;
    protected CltuGenerator cltuGenerator;
    final static String CLTU_START_SEQ_KEY = "cltuStartSequence";
    final static String CLTU_TAIL_SEQ_KEY = "cltuTailSequence";

    @Override
    public Spec getDefaultSpec() {
        var spec = super.getDefaultSpec();

        spec.addOption("frameType", OptionType.STRING).withChoices(CcsdsFrameType.class);
        spec.addOption("clcwStream", OptionType.STRING);
        spec.addOption("goodFrameStream", OptionType.STRING);
        spec.addOption("badFrameStream", OptionType.STRING);

        spec.addOption("spacecraftId", OptionType.INTEGER);
        spec.addOption("physicalChannelName", OptionType.STRING);
        spec.addOption("errorDetection", OptionType.STRING);

        spec.addOption("frameLength", OptionType.INTEGER);
        spec.addOption("insertZoneLength", OptionType.INTEGER);
        spec.addOption("frameHeaderErrorControlPresent", OptionType.BOOLEAN);
        spec.addOption("virtualChannels", OptionType.LIST).withElementType(OptionType.ANY);
        spec.addOption("maxFrameLength", OptionType.INTEGER);
        spec.addOption("minFrameLength", OptionType.INTEGER);
        spec.addOption("priorityScheme", OptionType.STRING)
                .withChoices(PriorityScheme.class)
                .withDefault(PriorityScheme.FIFO);

        spec.addOption("skipRandomizationForVcs", OptionType.LIST).withElementType(OptionType.INTEGER);
        spec.addOption("cltuEncoding", OptionType.STRING);
        spec.addOption(CLTU_START_SEQ_KEY, OptionType.STRING);
        spec.addOption(CLTU_TAIL_SEQ_KEY, OptionType.STRING);
        spec.addOption("randomizeCltu", OptionType.BOOLEAN);
        spec.addOption("cltuGeneratorClassName", OptionType.STRING);
        spec.addOption("cltuGeneratorArgs", OptionType.MAP).withSpec(Spec.ANY);

        return spec;
    }

    @Override
    public void init(String yamcsInstance, String linkName, YConfiguration config) {
        super.init(yamcsInstance, linkName, config);
        if (config.containsKey("skipRandomizationForVcs")) {
            List<Integer> l = config.getList("skipRandomizationForVcs");
            if (!l.isEmpty()) {
                int[] a = l.stream().mapToInt(i -> i).toArray();
                skipRandomizationForVcs = IntArray.wrap(a);
                skipRandomizationForVcs.sort();
            }
        }
        String cltuEncoding = config.getString("cltuEncoding", null);
        if (cltuEncoding != null) {
            if ("BCH".equals(cltuEncoding)) {
                byte[] startSeq = config.getBinary(CLTU_START_SEQ_KEY, BchCltuGenerator.CCSDS_START_SEQ);
                byte[] tailSeq = config.getBinary(CLTU_TAIL_SEQ_KEY, BchCltuGenerator.CCSDS_TAIL_SEQ);
                this.randomize = config.getBoolean("randomizeCltu", false);
                cltuGenerator = new BchCltuGenerator(startSeq, tailSeq);
            } else if ("LDPC64".equals(cltuEncoding)) {
                checkSuperfluosLdpcRandomizationOption(config);
                byte[] startSeq = config.getBinary(CLTU_START_SEQ_KEY, Ldpc64CltuGenerator.CCSDS_START_SEQ);
                byte[] tailSeq = config.getBinary(CLTU_TAIL_SEQ_KEY, CltuGenerator.EMPTY_SEQ);
                cltuGenerator = new Ldpc64CltuGenerator(startSeq, tailSeq);
                this.randomize = true;
            } else if ("LDPC256".equals(cltuEncoding)) {
                checkSuperfluosLdpcRandomizationOption(config);
                byte[] startSeq = config.getBinary(CLTU_START_SEQ_KEY, Ldpc256CltuGenerator.CCSDS_START_SEQ);
                byte[] tailSeq = config.getBinary(CLTU_TAIL_SEQ_KEY, CltuGenerator.EMPTY_SEQ);
                cltuGenerator = new Ldpc256CltuGenerator(startSeq, tailSeq);
                this.randomize = true;
            } else if ("CUSTOM".equals(cltuEncoding)) {
                String cltuGeneratorClassName = config.getString("cltuGeneratorClassName", null);
                if (cltuGeneratorClassName == null) {
                    throw new ConfigurationException("CUSTOM cltu generator requires value for cltuGeneratorClassName");
                }
                if (!config.containsKey("cltuGeneratorArgs")) {
                    cltuGenerator = YObjectLoader.loadObject(cltuGeneratorClassName);
                } else {
                    YConfiguration args = config.getConfig("cltuGeneratorArgs");
                    cltuGenerator = YObjectLoader.loadObject(cltuGeneratorClassName, args);
                }
                this.randomize = config.getBoolean("randomizeCltu", false);
            } else {
                throw new ConfigurationException(
                        "Invalid value '" + cltuEncoding
                                + " for cltu. Valid values are BCH, LDPC64, LDPC256, or CUSTOM");
            }
        }

        multiplexer = new MasterChannelFrameMultiplexer(yamcsInstance, linkName, config);
        subLinks = new ArrayList<>();
        for (VcUplinkHandler vch : multiplexer.getVcHandlers()) {
            if (vch instanceof Link) {
                Link l = (Link) vch;
                subLinks.add(l);
                l.setParent(this);
            }
        }
    }

    static void checkSuperfluosLdpcRandomizationOption(YConfiguration config) {
        if (!config.getBoolean("randomizeCltu", true)) {
            throw new ConfigurationException(
                    "CLTU randomization is always enabled for the LDPC codec, please remove the randomizeCltu option");
        }
    }

    /**
     * optionally encode the data to CLTU if the CLTU generator is configured.
     * <p>
     * Randomization will also be performed if configured.
     */
    protected byte[] encodeCltu(int vcId, byte[] data) {
        if (cltuGenerator != null) {
            boolean rand = randomize
                    && (skipRandomizationForVcs == null || skipRandomizationForVcs.binarySearch(vcId) < 0);
            return cltuGenerator.makeCltu(data, rand);
        } else {
            return data;
        }

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
    public boolean sendCommand(PreparedCommand preparedCommand) {
        throw new ConfigurationException(
                "This class cannot send command directly, please remove the stream associated to the main link");
    }

    /**
     * Ack the BD frames Note: the AD frames are acknowledged in the when the COP1 ack is received
     * 
     * @param tf
     */
    protected void ackBypassFrame(TcTransferFrame tf) {
        if (tf.getCommands() != null) {
            for (PreparedCommand pc : tf.getCommands()) {
                commandHistoryPublisher.publishAck(pc.getCommandId(), CommandHistoryPublisher.AcknowledgeSent_KEY,
                        timeService.getMissionTime(), AckStatus.OK);
            }
        }
    }

    protected void failBypassFrame(TcTransferFrame tf, String reason) {
        if (tf.getCommands() != null) {
            for (PreparedCommand pc : tf.getCommands()) {
                commandHistoryPublisher.publishAck(pc.getCommandId(), CommandHistoryPublisher.AcknowledgeSent_KEY,
                        TimeEncoding.getWallclockTime(), AckStatus.NOK, reason);

                commandHistoryPublisher.commandFailed(pc.getCommandId(), timeService.getMissionTime(), reason);
            }
        }
    }

}
