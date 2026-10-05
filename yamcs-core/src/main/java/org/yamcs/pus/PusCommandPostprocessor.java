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

    /**
     * Top-level PUS 11 keys of older Pixxel releases, now configured in the {@code pus11} block. Rejected so that an
     * old configuration does not silently produce TC(11,4) commands without the sub-schedule and group id fields.
     */
    private static final String[] LEGACY_PUS11_KEYS = { "pus11SourceId", "pus11AckFlags", "pus11SubscheduleId",
            "pus11GroupId" };

    /**
     * Only registered when the {@code pus11} block is present in the post-processor configuration - a link that
     * doesn't declare support for PUS 11 scheduling doesn't offer this option (keeps it from cluttering command
     * forms on links that only support PUS 22 scheduling, or neither).
     */
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

    /**
     * Orbit number of the position tag to insert the command at (ST[22] position-based scheduling). Setting this
     * (together with {@link #OPTION_ORBIT_ANGLE}) wraps the command into a TC(22,4). Only registered when the
     * {@code pus22} block is present in the post-processor configuration.
     */
    public static final CommandOption OPTION_ORBIT_NUMBER = new CommandOption("pus22OrbitNumber",
            "PUS 22 Orbit Number", CommandOptionType.NUMBER)
                    .withHelp("Orbit number of the position at which to release the command; required together with pus22OrbitAngle");

    /**
     * Orbit angle (degrees, 0-360) of the position tag to insert the command at. Its mere presence is what triggers
     * wrapping the command into a TC(22,4), exactly like {@link #OPTION_SCHEDULE_TIME} triggers a TC(11,4).
     */
    public static final CommandOption OPTION_ORBIT_ANGLE = new CommandOption("pus22OrbitAngle",
            "PUS 22 Orbit Angle (deg)", CommandOptionType.NUMBER)
                    .withHelp("If set (together with pus22OrbitNumber), embed this command into a PUS 22 insert-activities command");

    /**
     * Sub-schedule to insert the command into. Only registered (and only written into the TC(22,4)) when the
     * {@code pus22 -> subScheduleId} block is present in the post-processor configuration.
     */
    public static final CommandOption OPTION_SUB_SCHEDULE_ID_22 = new CommandOption("pus22SubScheduleId",
            "PUS 22 Sub-schedule Id", CommandOptionType.NUMBER)
                    .withHelp("Sub-schedule to insert the command into when it is scheduled via pus22OrbitAngle");

    /**
     * Scheduling group to associate the command with. Only registered (and only written into the TC(22,4)) when the
     * {@code pus22 -> groupId} block is present in the post-processor configuration.
     */
    public static final CommandOption OPTION_GROUP_ID_22 = new CommandOption("pus22GroupId",
            "PUS 22 Scheduling Group Id", CommandOptionType.NUMBER)
                    .withHelp("Scheduling group to associate the command with when it is scheduled via pus22OrbitAngle");

    // raw binary angle measure: ticks in [0, TICKS_PER_ORBIT) represent [0, 360) degrees. Mirrors
    // org.yamcs.simulator.pus.PusPosition.TICKS_PER_ORBIT (duplicated: yamcs-core cannot depend on the simulator).
    static final int TICKS_PER_ORBIT = 1 << 16;
    static final int POSITION_TAG_LENGTH_BYTES = 6; // 4 bytes orbit number + 2 bytes angle ticks

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
     * If true, the generated PUS(11,4) commands will contain a CRC. Requires errorDetection.
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

    int pus22SourceId = 0;
    /**
     * If true, the generated PUS(22,4) commands will contain a CRC. Requires errorDetection.
     */
    boolean pus22Crc;
    /**
     * if it is different than -1 it will be used as the APID for the TC(22,4)
     */
    int pus22Apid = -1;
    // TC(22,4) sub-schedule id field: enabled and sized via the "pus22 -> subScheduleId" config block.
    int pus22SubScheduleIdBytes = 0;
    long pus22SubScheduleIdDefault = 0;
    // TC(22,4) scheduling group id field: enabled and sized via the "pus22 -> groupId" config block.
    int pus22GroupIdBytes = 0;
    long pus22GroupIdDefault = 0;

    // allow changing the sequence count during runtime
    private ChangeSeqCountAction seqCountAction = new ChangeSeqCountAction();


    @Override
    public void init(String yamcsInstance, YConfiguration config, Link link) {
        super.init(yamcsInstance, config, link);
        for (String key : LEGACY_PUS11_KEYS) {
            if (config.containsKey(key)) {
                throw new ConfigurationException(
                        key + " is no longer supported; configure it in the pus11 block (see the PUS command postprocessor documentation)");
            }
        }
        this.pus11Apid = getIntInRange(config, "pus11Apid", -1, -1, 0x7FF);
        this.pus22Apid = getIntInRange(config, "pus22Apid", -1, -1, 0x7FF);
        if (link instanceof LinkActionProvider lap) {
            lap.addAction(seqCountAction);
        }

        errorDetectionCalculator = AbstractPacketPreprocessor.getErrorDetectionWordCalculator(config);
        this.pus11Crc = getScheduleCrc(config, "pus11Crc");
        this.pus22Crc = getScheduleCrc(config, "pus22Crc");
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
            pus11SourceId = getIntInRange(pus11Config, "sourceId", pus11SourceId, 0, 0xFFFF);
            pus11AckFlags = getIntInRange(pus11Config, "ackFlags", pus11AckFlags, 0, 0xF);
            addCommandOption(OPTION_SCHEDULE_TIME);

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

        if (config.containsKey("pus22")) {
            YConfiguration pus22Config = config.getConfig("pus22");
            pus22SourceId = getIntInRange(pus22Config, "sourceId", pus22SourceId, 0, 0xFFFF);
            addCommandOption(OPTION_ORBIT_NUMBER);
            addCommandOption(OPTION_ORBIT_ANGLE);

            if (pus22Config.containsKey("subScheduleId")) {
                YConfiguration ssConfig = pus22Config.getConfig("subScheduleId");
                pus22SubScheduleIdBytes = ssConfig.getInt("bytes", 1);
                pus22SubScheduleIdDefault = ssConfig.getLong("default", 0);
                if (pus22SubScheduleIdBytes < 1 || pus22SubScheduleIdBytes > 8) {
                    throw new ConfigurationException("pus22.subScheduleId.bytes must be between 1 and 8");
                }
                addCommandOption(OPTION_SUB_SCHEDULE_ID_22);
            }
            if (pus22Config.containsKey("groupId")) {
                YConfiguration groupConfig = pus22Config.getConfig("groupId");
                pus22GroupIdBytes = groupConfig.getInt("bytes", 1);
                pus22GroupIdDefault = groupConfig.getLong("default", 0);
                if (pus22GroupIdBytes < 1 || pus22GroupIdBytes > 8) {
                    throw new ConfigurationException("pus22.groupId.bytes must be between 1 and 8");
                }
                addCommandOption(OPTION_GROUP_ID_22);
            }
        }
    }

    private static void addCommandOption(CommandOption option) {
        var server = YamcsServer.getServer();
        if (!server.hasCommandOption(option.getId())) {
            server.addCommandOption(option);
        }
    }

    /**
     * The CRC of a generated TC(11,4)/TC(22,4) defaults to whether errorDetection is configured; enabling it without
     * errorDetection is a configuration error.
     */
    private boolean getScheduleCrc(YConfiguration config, String key) {
        if (config.containsKey(key)) {
            boolean crc = config.getBoolean(key);
            if (crc && errorDetectionCalculator == null) {
                throw new ConfigurationException(key + " is enabled but no errorDetection is configured");
            }
            return crc;
        } else {
            return errorDetectionCalculator != null;
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
                long subScheduleId = getIdOption(pc, OPTION_SUB_SCHEDULE_ID, pus11SubScheduleIdDefault,
                        pus11SubScheduleIdBytes);
                long groupId = getIdOption(pc, OPTION_GROUP_ID, pus11GroupIdDefault, pus11GroupIdBytes);
                // We have embed the command into a PUS(11,4) insert into schedule TC
                binary = buildScheduledTc(pc.getCommandId(), scheduleTime, subScheduleId, groupId, binary);
            } catch (Exception e) {
                String msg = "Error building the TC(11,4) command " + e.getMessage();
                log.warn(msg);
                failCommand(pc.getCommandId(), msg);
                return null;
            }
        } else if (pc.getAttribute(OPTION_ORBIT_ANGLE.getId()) != null) {
            if (pc.getAttribute(OPTION_ORBIT_NUMBER.getId()) == null) {
                String msg = "pus22OrbitNumber is required when pus22OrbitAngle is set";
                log.warn(msg);
                failCommand(pc.getCommandId(), msg);
                return null;
            }
            try {
                long orbitNumber = attributeAsLong(pc, OPTION_ORBIT_NUMBER.getId(), 0);
                double angleDeg = attributeAsDouble(pc, OPTION_ORBIT_ANGLE.getId(), 0);
                int angleTicks = (int) Math.floorMod(Math.round(angleDeg * TICKS_PER_ORBIT / 360.0),
                        (long) TICKS_PER_ORBIT);
                long subScheduleId = getIdOption(pc, OPTION_SUB_SCHEDULE_ID_22, pus22SubScheduleIdDefault,
                        pus22SubScheduleIdBytes);
                long groupId = getIdOption(pc, OPTION_GROUP_ID_22, pus22GroupIdDefault, pus22GroupIdBytes);
                // We have embed the command into a PUS(22,4) insert into schedule TC
                binary = buildScheduledPositionTc(pc.getCommandId(), orbitNumber, angleTicks, subScheduleId, groupId,
                        binary);
            } catch (Exception e) {
                String msg = "Error building the TC(22,4) command " + e.getMessage();
                log.warn(msg);
                failCommand(pc.getCommandId(), msg);
                return null;
            }
        }
        return binary;
    }


    byte[] buildScheduledTc(CommandId cmdId, long scheduleTime, long subScheduleId, long groupId, byte[] binary) {
        // The TC(11,4) has to be addressed to the application process hosting the on-board scheduler, which is in
        // general not the one of the embedded command (ECSS 6.11.2.2)
        if (pus11Apid < 0) {
            throw new IllegalStateException(
                    "pus11Apid must be configured to schedule commands with " + OPTION_SCHEDULE_TIME.getId());
        }
        byte[] scheduleTcPacket = new byte[getScheduledTcOverhead() + binary.length];
        var tc = CcsdsPacket.wrap(scheduleTcPacket);
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
        // sub-schedule id (only if sub-schedules are configured)
        offset += encodeUnsigned(subScheduleId, scheduleTcPacket, offset, pus11SubScheduleIdBytes);
        // N (number of commands scheduled)
        scheduleTcPacket[offset++] = 1;
        // group id of the single activity (only if groups are configured)
        offset += encodeUnsigned(groupId, scheduleTcPacket, offset, pus11GroupIdBytes);

        if (tcoService == null) {
            // no time correlation: encode the release time against the configured timeEncoding.epoch
            offset += timeEncoder.encode(shiftToEpoch(scheduleTime), scheduleTcPacket, offset);
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
     * Builds a TC(22,4) "insert activities into the position-based schedule" wrapping a single activity, the
     * position-based mirror of {@link #buildScheduledTc}. Unlike time, no correlation service is needed: the
     * caller supplies the absolute target position tag (orbit number + angle) directly.
     */
    byte[] buildScheduledPositionTc(CommandId cmdId, long orbitNumber, int angleTicks, long subScheduleId,
            long groupId, byte[] binary) {

        // 6 bytes primary header
        // 5 bytes secondary header
        // pus22SubScheduleIdBytes bytes sub-schedule id (0 if sub-schedules not configured)
        // 1 byte N
        // pus22GroupIdBytes bytes group id (0 if groups not configured)
        // POSITION_TAG_LENGTH_BYTES bytes position tag (orbit number + angle)
        int scheduleTcLength = 12 + pus22SubScheduleIdBytes + pus22GroupIdBytes + POSITION_TAG_LENGTH_BYTES
                + binary.length;
        if (pus22Crc) {
            scheduleTcLength += 2;
        }
        byte[] scheduleTcPacket = new byte[scheduleTcLength];
        var tc = CcsdsPacket.wrap(scheduleTcPacket);
        int apid = pus22Apid >= 0 ? pus22Apid : CcsdsPacket.getAPID(binary);
        tc.setHeader(apid,
                /*tmtc*/ 1,
                /*secondary header present*/1,
                /*unsegmented data*/3,
                /*seq count(it will be filled in later)*/ 0);

        int offset = 6;
        // 4 bits TC PUS version number = 2, 4 bits = ackflags.
        scheduleTcPacket[offset++] = ((byte) 0x2D);
        // type = 22
        scheduleTcPacket[offset++] = 22;
        // subtype = 4
        scheduleTcPacket[offset++] = 4;
        // source id
        ByteArrayUtils.encodeUnsignedShort(pus22SourceId, scheduleTcPacket, offset);
        offset += 2;
        // sub-schedule id (only if sub-schedules are configured)
        offset += encodeUnsigned(subScheduleId, scheduleTcPacket, offset, pus22SubScheduleIdBytes);
        // N (number of activities scheduled)
        scheduleTcPacket[offset++] = 1;
        // group id of the single activity (only if groups are configured)
        offset += encodeUnsigned(groupId, scheduleTcPacket, offset, pus22GroupIdBytes);
        // position tag: 4 bytes orbit number + 2 bytes angle ticks
        ByteArrayUtils.encodeInt((int) orbitNumber, scheduleTcPacket, offset);
        offset += 4;
        ByteArrayUtils.encodeUnsignedShort(angleTicks, scheduleTcPacket, offset);
        offset += 2;

        System.arraycopy(binary, 0, scheduleTcPacket, offset, binary.length);

        int seqCount = seqFiller.fill(scheduleTcPacket); // write sequence count

        commandHistoryPublisher.publish(cmdId, "pus22Apid", apid);
        commandHistoryPublisher.publish(cmdId, "pus22CcsdsSeqCount", seqCount);
        // subScheduleId/groupId are not republished here, same reasoning as buildScheduledTc(): they are already
        // recorded, correctly typed, as the pus22SubScheduleId/pus22GroupId command option attributes.

        if (pus22Crc) {
            int pos = scheduleTcPacket.length - 2;
            int checkword = errorDetectionCalculator.compute(scheduleTcPacket, 0, pos);
            log.debug("Appending checkword on position {}: {}", pos, Integer.toHexString(checkword));
            ByteArrayUtils.encodeUnsignedShort(checkword, scheduleTcPacket, pos);
        }
        commandHistoryPublisher.publish(cmdId, "pus22Binary", scheduleTcPacket);

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

    /**
     * Reads a sub-schedule/group id option and checks that it fits the {@code nbytes} wide field it is written into,
     * rather than silently truncating it (web clients send numbers as doubles, so fractional values are rejected too)
     */
    private static long getIdOption(PreparedCommand pc, CommandOption option, long dflt, int nbytes) {
        CommandHistoryAttribute cha = pc.getAttribute(option.getId());
        if (cha == null || nbytes == 0) {
            return dflt;
        }
        Value v = cha.getValue();
        if ((v.getType() == Value.Type.DOUBLE && v.getDoubleValue() != Math.rint(v.getDoubleValue()))
                || (v.getType() == Value.Type.FLOAT && v.getFloatValue() != Math.rint(v.getFloatValue()))) {
            throw new IllegalArgumentException("invalid " + option.getId() + " "
                    + attributeAsDouble(pc, option.getId(), 0) + ", expected an integer");
        }
        long id = attributeAsLong(pc, option.getId(), dflt);
        if (id < 0 || (nbytes < 8 && id >= 1L << (8 * nbytes))) {
            throw new IllegalArgumentException("invalid " + option.getId() + " " + id + ", expected an integer 0-"
                    + (nbytes < 8 ? (1L << (8 * nbytes)) - 1 : Long.MAX_VALUE));
        }
        return id;
    }

    private static double attributeAsDouble(PreparedCommand pc, String id, double dflt) {
        CommandHistoryAttribute cha = pc.getAttribute(id);
        if (cha == null) {
            return dflt;
        }
        Value v = cha.getValue();
        return switch (v.getType()) {
        case DOUBLE -> v.getDoubleValue();
        case FLOAT -> v.getFloatValue();
        case SINT32 -> v.getSint32Value();
        case SINT64 -> v.getSint64Value();
        case UINT32 -> v.getUint32Value() & 0xFFFFFFFFL;
        case UINT64 -> v.getUint64Value();
        case STRING -> Double.parseDouble(v.getStringValue().trim());
        default -> dflt;
        };
    }

    @Override
    public int getBinaryLength(PreparedCommand pc) {
        int len = pc.getBinary().length;
        if (hasCrc(pc)) {
            len += 2;
        }
        if (isScheduled(pc)) {
            // the command is wrapped into a TC(11,4)
            len += getScheduledTcOverhead();
        } else if (pc.getAttribute(OPTION_ORBIT_ANGLE.getId()) != null) {
            // the command is wrapped into a TC(22,4); keep in sync with buildScheduledPositionTc()
            len += 12 + pus22SubScheduleIdBytes + pus22GroupIdBytes + POSITION_TAG_LENGTH_BYTES;
            if (pus22Crc) {
                len += 2;
            }
        }
        return len;
    }

    /**
     * Number of bytes the TC(11,4) wrapper adds to the embedded command: 6 bytes primary header, 5 bytes secondary
     * header, the optional sub-schedule id, 1 byte N, the optional group id, the release time and the optional CRC
     */
    int getScheduledTcOverhead() {
        return 12 + pus11SubScheduleIdBytes + pus11GroupIdBytes + timeEncoder.getEncodedLength()
                + (pus11Crc ? 2 : 0);
    }

    private static boolean isScheduled(PreparedCommand pc) {
        return pc.getAttribute(OPTION_SCHEDULE_TIME.getId()) != null;
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
