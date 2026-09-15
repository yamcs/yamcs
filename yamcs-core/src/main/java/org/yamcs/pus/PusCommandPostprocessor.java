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
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.tctm.AbstractCommandPostProcessor;
import org.yamcs.tctm.AbstractPacketPreprocessor;
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

    public static final CommandOption OPTION_SCHEDULE_TIME = new CommandOption("pus11ScheduleAt", "Schedule Time",
            CommandOptionType.TIMESTAMP).withHelp("If set, embeed this command into a PUS 11 SCHEDULE_TC commad");

    /**
     * Sub-schedule to insert the command into. Only registered (and only written into the TC(11,4)) when the
     * {@code pus11 -> subScheduleId} block is present in the post-processor configuration.
     */
    public static final CommandOption OPTION_SUB_SCHEDULE_ID = new CommandOption("pus11SubScheduleId",
            "PUS 11 Sub-schedule Id", CommandOptionType.NUMBER)
                    .withHelp("Sub-schedule to insert the command into when it is scheduled via pus11ScheduleAt");

    /**
     * Scheduling group to associate the command with. Only registered (and only written into the TC(11,4)) when the
     * {@code pus11 -> groupId} block is present in the post-processor configuration.
     */
    public static final CommandOption OPTION_GROUP_ID = new CommandOption("pus11GroupId",
            "PUS 11 Scheduling Group Id", CommandOptionType.NUMBER)
                    .withHelp("Scheduling group to associate the command with when it is scheduled via pus11ScheduleAt");

    static {
        YamcsServer.getServer().addCommandOption(OPTION_SCHEDULE_TIME);
    }

    ErrorDetectionWordCalculator errorDetectionCalculator;
    protected CcsdsSeqCountFiller seqFiller = new CcsdsSeqCountFiller();

    protected CucTimeEncoder timeEncoder;
    TimeCorrelationService tcoService;

    int pus11SourceId = 0;
    /**
     * If true, the generated PUS(11,4) commands will contain a CRC
     */
    boolean pus11Crc;
    /**
     * if it is different than -1 it will be used as the APID for the TC(11,4)
     */
    int pus11Apid = -1;

    // TC(11,4) sub-schedule id field: enabled and sized via the "pus11 -> subScheduleId" config block.
    // width in bytes (0 = the field is not present in the TC(11,4))
    int pus11SubScheduleIdBytes = 0;
    long pus11SubScheduleIdDefault = 0;
    // TC(11,4) scheduling group id field: enabled and sized via the "pus11 -> groupId" config block.
    int pus11GroupIdBytes = 0;
    long pus11GroupIdDefault = 0;

    // allow changing the sequence count during runtime
    private ChangeSeqCountAction seqCountAction = new ChangeSeqCountAction();


    @Override
    public void init(String yamcsInstance, YConfiguration config, Link link) {
        super.init(yamcsInstance, config, link);
        this.pus11Crc = config.getBoolean("pus11Crc", true);
        this.pus11Apid = config.getInt("pus11Apid", -1);
        if (link instanceof LinkActionProvider lap) {
            lap.addAction(seqCountAction);
        }

        errorDetectionCalculator = AbstractPacketPreprocessor.getErrorDetectionWordCalculator(config);
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

        if (config.containsKey("pus11")) {
            YConfiguration pus11Config = config.getConfig("pus11");
            pus11SourceId = pus11Config.getInt("sourceId", pus11SourceId);

            if (pus11Config.containsKey("subScheduleId")) {
                YConfiguration ssConfig = pus11Config.getConfig("subScheduleId");
                pus11SubScheduleIdBytes = ssConfig.getInt("bytes", 1);
                pus11SubScheduleIdDefault = ssConfig.getLong("default", 0);
                if (pus11SubScheduleIdBytes < 1 || pus11SubScheduleIdBytes > 8) {
                    throw new ConfigurationException("pus11.subScheduleId.bytes must be between 1 and 8");
                }
                addCommandOption(OPTION_SUB_SCHEDULE_ID);
            }
            if (pus11Config.containsKey("groupId")) {
                YConfiguration groupConfig = pus11Config.getConfig("groupId");
                pus11GroupIdBytes = groupConfig.getInt("bytes", 1);
                pus11GroupIdDefault = groupConfig.getLong("default", 0);
                if (pus11GroupIdBytes < 1 || pus11GroupIdBytes > 8) {
                    throw new ConfigurationException("pus11.groupId.bytes must be between 1 and 8");
                }
                addCommandOption(OPTION_GROUP_ID);
            }
        }
    }

    private static void addCommandOption(CommandOption option) {
        var server = YamcsServer.getServer();
        if (!server.hasCommandOption(option.getId())) {
            server.addCommandOption(option);
        }
    }

    private CucTimeEncoder configureTimeEncoding(YConfiguration config) {
        boolean implicitPfield = config.getBoolean("implicitPfield", true);
        int pfield1 = config.getInt("pfield");
        int pfield2 = config.getInt("pfieldCont", -1);

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

        if (pc.getAttribute("pus11ScheduleAt") != null) {
            try {
                long scheduleTime = pc.getAttribute("pus11ScheduleAt").getValue().getTimestampValue();
                long subScheduleId = attributeAsLong(pc, OPTION_SUB_SCHEDULE_ID.getId(), pus11SubScheduleIdDefault);
                long groupId = attributeAsLong(pc, OPTION_GROUP_ID.getId(), pus11GroupIdDefault);
                // We have embed the command into a PUS(11,4) insert into schedule TC
                binary = buildScheduledTc(pc.getCommandId(), scheduleTime, subScheduleId, groupId, binary);
            } catch (Exception e) {
                String msg = "Error building the TC(11,4) command " + e.getMessage();
                log.warn(msg);
                failCommand(pc.getCommandId(), msg);
                return null;
            }
        }
        return binary;
    }


    byte[] buildScheduledTc(CommandId cmdId, long scheduleTime, long subScheduleId, long groupId, byte[] binary) {

        // 6 bytes primary header
        // 5 bytes secondary header
        // pus11SubScheduleIdBytes bytes sub-schedule id (0 if sub-schedules not configured)
        // 1 byte N
        // pus11GroupIdBytes bytes group id (0 if groups not configured)
        // n bytes time
        int scheduleTcLength = 12 + pus11SubScheduleIdBytes + pus11GroupIdBytes
                + timeEncoder.getEncodedLength() + binary.length;
        if (pus11Crc) {
            scheduleTcLength += 2;
        }
        byte[] scheduleTcPacket = new byte[scheduleTcLength];
        var tc = CcsdsPacket.wrap(scheduleTcPacket);
        int apid = pus11Apid >= 0 ? pus11Apid : CcsdsPacket.getAPID(binary);
        tc.setHeader(apid,
                /*tmtc*/ 1,
                /*secondary header present*/1,
                /*unsegmented data*/3,
                /*seq count(it will be filled in later)*/ 0);

        int offset = 6;
        // 4 bits TC PUS version number = 2, 4 bits = ackflags.
        scheduleTcPacket[offset++] = ((byte) 0x2D);
        // type = 11
        scheduleTcPacket[offset++] = 11;
        // subtype = 4
        scheduleTcPacket[offset++] = 4;
        // source id
        ByteArrayUtils.encodeUnsignedShort(pus11SourceId, scheduleTcPacket, offset);
        offset += 2;
        // sub-schedule id (only if sub-schedules are configured)
        offset += encodeUnsigned(subScheduleId, scheduleTcPacket, offset, pus11SubScheduleIdBytes);
        // N (number of commands scheduled)
        scheduleTcPacket[offset++] = 1;
        // group id of the single activity (only if groups are configured)
        offset += encodeUnsigned(groupId, scheduleTcPacket, offset, pus11GroupIdBytes);

        if (tcoService == null) {
            // no time correlation: encode the release time against the Unix epoch, which the receiver is
            // expected to decode with timeEncoding.epoch = UNIX
            offset += timeEncoder.encode(TimeEncoding.toUnixMillisec(scheduleTime), scheduleTcPacket, offset);
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

        commandHistoryPublisher.publish(cmdId, "pus11Apid", apid);
        commandHistoryPublisher.publish(cmdId, "pus11CcsdsSeqCount", seqCount);
        // subScheduleId/groupId are not republished here: they are already recorded, correctly typed, as the
        // pus11SubScheduleId/pus11GroupId command option attributes (set by the operator or defaulted at init()).

        if (pus11Crc) {
            int pos = scheduleTcPacket.length - 2;
            int checkword = errorDetectionCalculator.compute(scheduleTcPacket, 0, pos);
            log.debug("Appending checkword on position {}: {}", pos, Integer.toHexString(checkword));
            ByteArrayUtils.encodeUnsignedShort(checkword, scheduleTcPacket, pos);
        }
        commandHistoryPublisher.publish(cmdId, "pus11Binary", scheduleTcPacket);

        return scheduleTcPacket;
    }

    /**
     * Big-endian encode the {@code nbytes} least significant bytes of {@code value} at {@code offset}. Returns
     * {@code nbytes} so callers can advance their offset; a {@code nbytes} of 0 is a no-op.
     */
    static int encodeUnsigned(long value, byte[] buf, int offset, int nbytes) {
        for (int i = nbytes - 1; i >= 0; i--) {
            buf[offset + i] = (byte) (value & 0xFF);
            value >>>= 8;
        }
        return nbytes;
    }

    private static long attributeAsLong(PreparedCommand pc, String id, long dflt) {
        CommandHistoryAttribute cha = pc.getAttribute(id);
        if (cha == null) {
            return dflt;
        }
        Value v = cha.getValue();
        return switch (v.getType()) {
        case SINT32 -> v.getSint32Value();
        case SINT64 -> v.getSint64Value();
        case UINT32 -> v.getUint32Value() & 0xFFFFFFFFL;
        case UINT64 -> v.getUint64Value();
        case DOUBLE -> (long) v.getDoubleValue();
        case FLOAT -> (long) v.getFloatValue();
        case STRING -> Long.parseLong(v.getStringValue().trim());
        default -> dflt;
        };
    }

    @Override
    public int getBinaryLength(PreparedCommand pc) {
        int len = pc.getBinary().length;
        if (hasCrc(pc)) {
            len += 2;
        }
        if (pc.getAttribute(OPTION_SCHEDULE_TIME.getId()) != null) {
            // the command is wrapped into a TC(11,4); keep in sync with buildScheduledTc()
            len += 12 + pus11SubScheduleIdBytes + pus11GroupIdBytes + timeEncoder.getEncodedLength();
            if (pus11Crc) {
                len += 2;
            }
        }
        return len;
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
