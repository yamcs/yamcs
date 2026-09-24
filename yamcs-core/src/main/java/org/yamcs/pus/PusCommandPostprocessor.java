package org.yamcs.pus;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.yamcs.CommandOption;
import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.CommandOption.CommandOptionType;
import org.yamcs.Spec.OptionType;
import org.yamcs.actions.ActionResult;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.tctm.AbstractCommandPostProcessor;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.tctm.AbstractPacketPreprocessor.TimeEpochs;
import org.yamcs.tctm.CcsdsPacket;
import org.yamcs.tctm.CcsdsSeqCountFiller;
import org.yamcs.tctm.ErrorDetectionWordCalculator;
import org.yamcs.tctm.Link;
import org.yamcs.tctm.LinkAction;
import org.yamcs.tctm.LinkActionProvider;
import org.yamcs.tctm.ccsds.time.CucTimeEncoder;
import org.yamcs.time.TimeCorrelationService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;

import com.google.gson.JsonObject;

import static org.yamcs.tctm.AbstractPacketPreprocessor.CONFIG_KEY_TCO_SERVICE;

public class PusCommandPostprocessor extends AbstractCommandPostProcessor {
    public static final String CCSDS_SEQCOUNT_PARA_NAME = "ccsds-seqcount";
    public static final String CCSDS_APID_PARA_NAME = "ccsds-apid";

    /**
     * Keys the PUS-1 verifiers in {@code pus.xml} read for the request ID they wait for. For a
     * {@code pus11ScheduleAt} command these are republished with the TC(11,4) wrapper's request ID
     * (see Gap #5) so the "Accepted"/"Started"/"Complete" ACKs that actually arrive within the
     * CheckWindow - the wrapper's, not the embedded command's - are the ones matched.
     */
    public static final String PUS11_INNER_APID_PARA_NAME = "pus11-inner-apid";
    public static final String PUS11_INNER_SEQCOUNT_PARA_NAME = "pus11-inner-seqcount";

    public static final CommandOption OPTION_SCHEDULE_TIME = new CommandOption("pus11ScheduleAt", "Schedule Time",
            CommandOptionType.TIMESTAMP).withHelp("If set, embeed this command into a PUS 11 SCHEDULE_TC commad");

    public static final CommandOption OPTION_SUBSCHEDULE_ID = new CommandOption("pus11SubscheduleId",
            "Sub-schedule ID", CommandOptionType.NUMBER)
            .withHelp("Sub-schedule (0-255) of the PUS 11 SCHEDULE_TC command; only used if Schedule Time is set");

    public static final CommandOption OPTION_GROUP_ID = new CommandOption("pus11GroupId",
            "Group ID", CommandOptionType.NUMBER)
            .withHelp("Group (0-255) of the PUS 11 SCHEDULE_TC command; only used if Schedule Time is set");

    /**
     * Length of the TC(11,4) fields around the embedded command, excluding the release time and the CRC: primary
     * header (6), secondary header (5), sub-schedule id (1), N (1) and group id (1)
     */
    static final int PUS11_FIXED_LENGTH = 14;

    static {
        YamcsServer.getServer().addCommandOption(OPTION_SCHEDULE_TIME);
        YamcsServer.getServer().addCommandOption(OPTION_SUBSCHEDULE_ID);
        YamcsServer.getServer().addCommandOption(OPTION_GROUP_ID);
    }

    ErrorDetectionWordCalculator errorDetectionCalculator;
    protected CcsdsSeqCountFiller seqFiller = new CcsdsSeqCountFiller();

    protected CucTimeEncoder timeEncoder;
    TimeCorrelationService tcoService;
    TimeEpochs timeEpoch = TimeEpochs.NONE;
    long customEpoch;
    boolean customEpochIncludeLeapSecond;

    int pus11SourceId = 0;
    /**
     * PUS TC acknowledgement flags (4 bits) of the generated PUS(11,4) commands
     */
    int pus11AckFlags = 0xD;
    /**
     * Sub-schedule of the generated PUS(11,4) commands, unless overridden by the command option
     */
    int pus11SubscheduleId = 0;
    /**
     * Group of the generated PUS(11,4) commands, unless overridden by the command option
     */
    int pus11GroupId = 0;
    /**
     * If true, the generated PUS(11,4) commands will contain a CRC. Requires errorDetection.
     */
    boolean pus11Crc;
    /**
     * if it is different than -1 it will be used as the APID for the TC(11,4)
     */
    int pus11Apid = -1;
    // allow changing the sequence count during runtime
    private ChangeSeqCountAction seqCountAction = new ChangeSeqCountAction();


    @Override
    public void init(String yamcsInstance, YConfiguration config, Link link) {
        super.init(yamcsInstance, config, link);
        this.pus11Apid = getIntInRange(config, "pus11Apid", -1, -1, 0x7FF);
        this.pus11SourceId = getIntInRange(config, "pus11SourceId", 0, 0, 0xFFFF);
        this.pus11AckFlags = getIntInRange(config, "pus11AckFlags", 0xD, 0, 0xF);
        this.pus11SubscheduleId = getIntInRange(config, "pus11SubscheduleId", 0, 0, 0xFF);
        this.pus11GroupId = getIntInRange(config, "pus11GroupId", 0, 0, 0xFF);
        if (link instanceof LinkActionProvider lap) {
            lap.addAction(seqCountAction);
        }

        errorDetectionCalculator = AbstractPacketPreprocessor.getErrorDetectionWordCalculator(config);
        if (config.containsKey("pus11Crc")) {
            pus11Crc = config.getBoolean("pus11Crc");
            if (pus11Crc && errorDetectionCalculator == null) {
                throw new ConfigurationException("pus11Crc is enabled but no errorDetection is configured");
            }
        } else {
            pus11Crc = errorDetectionCalculator != null;
        }
        if (config.containsKey("timeEncoding")) {
            timeEncoder = configureTimeEncoding(config.getConfig("timeEncoding"));
        } else {
            timeEncoder = new CucTimeEncoder(0x2e, true);
        }
        if (config.containsKey(CONFIG_KEY_TCO_SERVICE)) {
            String tcoServiceName = config.getString(CONFIG_KEY_TCO_SERVICE);
            tcoService = YamcsServer.getServer().getInstance(yamcsInstance).getService(TimeCorrelationService.class,
                    tcoServiceName);
            if (tcoService == null) {
                throw new ConfigurationException(
                        "Cannot find a time correlation service with name " + tcoServiceName);
            }
        }
        if (config.containsKey("seqCounterName")) {
            seqFiller = new CcsdsSeqCountFiller(config.getString("seqCounterName"));
        }
    }

    private CucTimeEncoder configureTimeEncoding(YConfiguration config) {
        boolean implicitPfield = config.getBoolean("implicitPfield", true);
        int pfield1 = config.getInt("pfield");
        int pfield2 = config.getInt("pfieldCont", -1);
        timeEpoch = config.getEnum("epoch", TimeEpochs.class, TimeEpochs.NONE);
        if (timeEpoch == TimeEpochs.CUSTOM) {
            customEpochIncludeLeapSecond = config.getBoolean("timeIncludesLeapSeconds", true);
            String epochs = config.getString("epochUTC");
            customEpoch = TimeEncoding.parse(epochs);
            if (!customEpochIncludeLeapSecond) {
                customEpoch = TimeEncoding.toUnixMillisec(customEpoch);
            }
        }

        return new CucTimeEncoder(pfield1, pfield2, implicitPfield);
    }

    @Override
    public byte[] process(PreparedCommand pc) {
        byte[] binary = pc.getBinary();

        boolean hasCrc = hasCrc(pc);
        if (hasCrc) { // 2 extra bytes for the checkword
            binary = Arrays.copyOf(binary, binary.length + 2);
        }

        ByteBuffer bb = ByteBuffer.wrap(binary);
        bb.putShort(4, (short) (binary.length - 7)); // write packet length
        int seqCount = seqFiller.fill(binary); // write sequence count
        int apid = CcsdsPacket.getAPID(binary);

        commandHistoryPublisher.publish(pc.getCommandId(), CCSDS_APID_PARA_NAME, apid);
        commandHistoryPublisher.publish(pc.getCommandId(), CCSDS_SEQCOUNT_PARA_NAME, seqCount);

        if (hasCrc) {
            int pos = binary.length - 2;
            try {
                int checkword = errorDetectionCalculator.compute(binary, 0, pos);
                log.debug("Appending checkword on position {}: {}", pos, Integer.toHexString(checkword));
                bb.putShort(pos, (short) checkword);
            } catch (IllegalArgumentException e) {
                String msg = "Error when computing checkword: " + e.getMessage();
                log.warn(msg);
                commandHistoryPublisher.commandFailed(pc.getCommandId(), TimeEncoding.getWallclockTime(), msg);
                return null;
            }
        }
        commandHistoryPublisher.publish(pc.getCommandId(), PreparedCommand.CNAME_BINARY, binary);

        if (isScheduled(pc)) {
            try {
                long scheduleTime = pc.getAttribute(OPTION_SCHEDULE_TIME.getId()).getValue().getTimestampValue();
                int subscheduleId = getIdOption(pc, OPTION_SUBSCHEDULE_ID, pus11SubscheduleId, "sub-schedule id");
                int groupId = getIdOption(pc, OPTION_GROUP_ID, pus11GroupId, "group id");
                // We have embed the command into a PUS(11,4) insert into schedule TC
                binary = buildScheduledTc(pc.getCommandId(), scheduleTime, subscheduleId, groupId, binary);
            } catch (Exception e) {
                String msg = "Error building the TC(11,4) command " + e.getMessage();
                log.warn(msg);
                failCommand(pc.getCommandId(), msg);
                return null;
            }
        }
        return binary;
    }


    byte[] buildScheduledTc(CommandId cmdId, long scheduleTime, int subscheduleId, int groupId,
            byte[] binary) {
        byte[] scheduleTcPacket = new byte[getScheduledTcOverhead() + binary.length];
        var tc = CcsdsPacket.wrap(scheduleTcPacket);
        if (pus11Apid < 0) {
            throw new IllegalStateException(
                    "pus11Apid must be configured to schedule commands with " + OPTION_SCHEDULE_TIME.getId());
        }
        int apid = pus11Apid;
        tc.setHeader(apid,
                /*tmtc*/ 1,
                /*secondary header present*/1,
                /*unsegmented data*/3,
                /*seq count(it will be filled in later)*/ 0);

        int offset = 6;
        // 4 bits TC PUS version number = 2, 4 bits = ackflags.
        scheduleTcPacket[offset++] = (byte) (0x20 | pus11AckFlags);
        // type = 11
        scheduleTcPacket[offset++] = 11;
        // subtype = 4
        scheduleTcPacket[offset++] = 4;
        // source id
        ByteArrayUtils.encodeUnsignedShort(pus11SourceId, scheduleTcPacket, offset);
        offset += 2;
        // schedule id
        scheduleTcPacket[offset++] = (byte) subscheduleId;
        // N (number of commands scheduled)
        scheduleTcPacket[offset++] = 1;
        // group id (of the single scheduled command)
        scheduleTcPacket[offset++] = (byte) groupId;

        if (tcoService == null) {
            long shiftedScheduleTime = shiftToEpoch(scheduleTime);
            offset += timeEncoder.encode(shiftedScheduleTime, scheduleTcPacket, offset);
        } else {
            long obt = tcoService.getObt(scheduleTime);
            if (obt == Long.MIN_VALUE) {
                failCommand(cmdId, "Time corelation coefficients not available");
                return null;
            }
            offset += timeEncoder.encodeRaw(obt, scheduleTcPacket, offset);
        }
        System.arraycopy(binary, 0, scheduleTcPacket, offset, binary.length);

        int seqCount = seqFiller.fill(scheduleTcPacket); // write sequence count

        // The inner command's own request ID, preserved under distinct keys (Gap #8) before the
        // keys the verifiers read (ccsds-apid/ccsds-seqcount) are overwritten below with the
        // wrapper's request ID (Gap #5): only the wrapper's ACKs arrive inside the CheckWindow.
        commandHistoryPublisher.publish(cmdId, PUS11_INNER_APID_PARA_NAME, CcsdsPacket.getAPID(binary));
        commandHistoryPublisher.publish(cmdId, PUS11_INNER_SEQCOUNT_PARA_NAME, CcsdsPacket.getSequenceCount(binary));

        commandHistoryPublisher.publish(cmdId, CCSDS_APID_PARA_NAME, apid);
        commandHistoryPublisher.publish(cmdId, CCSDS_SEQCOUNT_PARA_NAME, seqCount);

        commandHistoryPublisher.publish(cmdId, "pus11-apid", apid);
        commandHistoryPublisher.publish(cmdId, "pus11-ccsds-seqcount", seqCount);
        commandHistoryPublisher.publish(cmdId, "pus11-source-id", pus11SourceId);
        commandHistoryPublisher.publish(cmdId, "pus11-subschedule-id", subscheduleId);
        commandHistoryPublisher.publish(cmdId, "pus11-group-id", groupId);

        if (pus11Crc) {
            int pos = scheduleTcPacket.length - 2;
            int checkword = errorDetectionCalculator.compute(scheduleTcPacket, 0, pos);
            log.debug("Appending checkword on position {}: {}", pos, Integer.toHexString(checkword));
            ByteArrayUtils.encodeUnsignedShort(checkword, scheduleTcPacket, pos);
        }
        commandHistoryPublisher.publish(cmdId, "pus11-binary", scheduleTcPacket);

        return scheduleTcPacket;
    }

    private long shiftToEpoch(long t) {
        switch (timeEpoch) {
        case GPS:
            return TimeEncoding.toGpsTimeMillisec(t);
        case J2000:
            return TimeEncoding.toJ2000Millisec(t);
        case TAI:
            return TimeEncoding.toTaiMillisec(t);
        case UNIX:
            return TimeEncoding.toUnixMillisec(t);
        case CUSTOM:
            if (customEpochIncludeLeapSecond) {
                return t - customEpoch;
            } else {
                return TimeEncoding.toUnixMillisec(t) - customEpoch;
            }
        case NONE:
            return t;
        default:
            throw new IllegalStateException("Unknown epoch " + timeEpoch);
        }
    }

    @Override
    public int getBinaryLength(PreparedCommand pc) {
        int length = pc.getBinary().length;
        if (hasCrc(pc)) {
            length += 2;
        }
        if (isScheduled(pc)) {
            length += getScheduledTcOverhead();
        }
        return length;
    }

    /**
     * Number of bytes the TC(11,4) wrapper adds to the embedded command
     */
    int getScheduledTcOverhead() {
        return PUS11_FIXED_LENGTH + timeEncoder.getEncodedLength() + (pus11Crc ? 2 : 0);
    }

    private static boolean isScheduled(PreparedCommand pc) {
        return pc.getAttribute(OPTION_SCHEDULE_TIME.getId()) != null;
    }

    private int getIdOption(PreparedCommand pc, CommandOption option, int defaultValue, String what) {
        var attr = pc.getAttribute(option.getId());
        if (attr == null) {
            return defaultValue;
        }
        var value = attr.getValue();
        double id = switch (value.getType()) {
        case SINT32 -> value.getSint32Value();
        case UINT32 -> Integer.toUnsignedLong(value.getUint32Value());
        case SINT64 -> value.getSint64Value();
        case UINT64 -> value.getUint64Value();
        case FLOAT -> value.getFloatValue();
        case DOUBLE -> value.getDoubleValue();
        default -> throw new IllegalArgumentException("unexpected " + what + " type " + value.getType());
        };
        if (id != Math.rint(id) || id < 0 || id > 0xFF) {
            throw new IllegalArgumentException("invalid " + what + " " + id + ", expected an integer 0-255");
        }
        return (int) id;
    }

    private static int getIntInRange(YConfiguration config, String key, int defaultValue, int min, int max) {
        int value = config.getInt(key, defaultValue);
        if (value < min || value > max) {
            throw new ConfigurationException(key + " must be between " + min + " and " + max + ", got " + value);
        }
        return value;
    }

    private boolean hasCrc(PreparedCommand pc) {
        return (errorDetectionCalculator != null);
    }

    private class ChangeSeqCountAction extends LinkAction {

        ChangeSeqCountAction() {
            super("change-seq-count", "Change sequence count for the outgoing commands");
        }

        @Override
        public Spec getSpec() {
            var spec = new Spec();
            spec.addOption("apid", OptionType.INTEGER)
                    .withRequired(true);
            spec.addOption("seq-count", OptionType.INTEGER)
                    .withRequired(true)
                    .withDefault(0);
            return spec;
        }

        @Override
        public void execute(Link link, JsonObject request, ActionResult result) {
            int apid = request.get("apid").getAsInt();
            int seqCount = request.get("seq-count").getAsInt();
            log.info("Changing Sequence count for APID {} to {}", apid, seqCount);

            seqFiller.setSequence(apid, seqCount);
            result.complete();
        }
    }
}
