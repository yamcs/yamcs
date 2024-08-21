package org.yamcs.pus;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.yamcs.CommandOption;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.CommandOption.CommandOptionType;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.tctm.AbstractCommandPostProcessor;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.tctm.CcsdsPacket;
import org.yamcs.tctm.CcsdsSeqCountFiller;
import org.yamcs.tctm.ErrorDetectionWordCalculator;
import org.yamcs.tctm.ccsds.time.CucTimeEncoder;
import org.yamcs.time.TimeCorrelationService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;

import static org.yamcs.tctm.AbstractPacketPreprocessor.CONFIG_KEY_TCO_SERVICE;

public class PusCommandPostprocessor extends AbstractCommandPostProcessor {

    public static final CommandOption OPTION_SCHEDULE_TIME = new CommandOption("pus11ScheduleAt", "Schedule Time",
            CommandOptionType.TIMESTAMP).withHelp("If set, embeed this command into a PUS 11 SCHEDULE_TC commad");

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


    @Override
    public void init(String yamcsInstance, YConfiguration config) {
        super.init(yamcsInstance, config);
        this.pus11Crc = config.getBoolean("pus11Crc", true);
        this.pus11Apid = config.getInt("pus11Apid", -1);

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

        commandHistoryPublisher.publish(pc.getCommandId(), "ccsds-seqcount", seqCount);

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
                // We have embed the command into a PUS(11,4) insert into schedule TC
                binary = buildScheduledTc(pc.getCommandId(), scheduleTime, binary);
            } catch (Exception e) {
                String msg = "Error building the TC(11,4) command " + e.getMessage();
                log.warn(msg);
                failCommand(pc.getCommandId(), msg);
                return null;
            }
        }
        return binary;
    }


    byte[] buildScheduledTc(CommandId cmdId, long scheduleTime, byte[] binary) {


        // 6 bytes primary header
        // 5 bytes secondary header
        // 1 byte schedule-id
        // 1 byte N
        // n bytes time
        int scheduleTcLength = 13 + timeEncoder.getEncodedLength() + binary.length;
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
        // schedule id
        scheduleTcPacket[offset++] = 1; // TODO scheduleId;
        // N (number of commands scheduled)
        scheduleTcPacket[offset++] = 1;

        if (tcoService == null) {
            offset += timeEncoder.encode(scheduleTime, scheduleTcPacket, offset);
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

        commandHistoryPublisher.publish(cmdId, "pus11-apid", apid);
        commandHistoryPublisher.publish(cmdId, "pus11-ccsds-seqcount", seqCount);

        if (pus11Crc) {
            int pos = scheduleTcPacket.length - 2;
            int checkword = errorDetectionCalculator.compute(scheduleTcPacket, 0, pos);
            log.debug("Appending checkword on position {}: {}", pos, Integer.toHexString(checkword));
            ByteArrayUtils.encodeUnsignedShort(checkword, scheduleTcPacket, pos);
        }
        commandHistoryPublisher.publish(cmdId, "pus11-binary", scheduleTcPacket);

        return scheduleTcPacket;
    }

    @Override
    public int getBinaryLength(PreparedCommand pc) {
        byte[] binary = pc.getBinary();
        if (hasCrc(pc)) {
            return binary.length + 2;
        } else {
            return binary.length;
        }
    }

    private boolean hasCrc(PreparedCommand pc) {
        byte[] binary = pc.getBinary();
        boolean secHeaderFlag = CcsdsPacket.getSecondaryHeaderFlag(binary);
        if (secHeaderFlag) {
            return (errorDetectionCalculator != null);
        } else {
            return false;
        }
    }
}
