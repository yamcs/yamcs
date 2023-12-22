package org.yamcs.tctm.pus;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.tctm.CcsdsPacket;
import org.yamcs.tctm.CcsdsSeqCountFiller;
import org.yamcs.tctm.CommandPostprocessor;
import org.yamcs.tctm.ErrorDetectionWordCalculator;
import org.yamcs.tctm.pus.services.tc.PusTcManager;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;

public class PusCommandPostprocessor implements CommandPostprocessor {
    static Logger log = LoggerFactory.getLogger(PusCommandPostprocessor.class);

    ErrorDetectionWordCalculator errorDetectionCalculator;
    protected CcsdsSeqCountFiller seqFiller = new CcsdsSeqCountFiller();

    protected CommandHistoryPublisher commandHistoryListener;

    PusTcManager pusTcManager;
    protected TimeService timeService;

    public static final int DEFAULT_TIMETAG_BUFFER = 5;     // Seconds
    public static int timetagBuffer;

    public PusCommandPostprocessor(String yamcsInstance) {
        this(yamcsInstance, null);
    }

    public PusCommandPostprocessor(String yamcsInstance, YConfiguration config) {
        errorDetectionCalculator = AbstractPacketPreprocessor.getErrorDetectionWordCalculator(config);

        if (config == null) {
            config = YConfiguration.emptyConfig();
        }
        YConfiguration pusConfig = config.getConfigOrEmpty("pus");

        timetagBuffer = config.getInt("timetagBuffer", DEFAULT_TIMETAG_BUFFER);
        timeService = YamcsServer.getTimeService(yamcsInstance);

        pusTcManager = new PusTcManager(yamcsInstance, pusConfig);
    }

    public void partialProcess(PreparedCommand pc) {
        byte[] binary = pc.getBinary();
        boolean hasCrc = hasCrc(pc);

        if (hasCrc) { // 2 extra bytes for the checkword
            // Fixme: Does the spare field (the one outside the secondary header) need to be added?
            binary = Arrays.copyOf(binary, binary.length + 2);
        }

        ByteBuffer bb = ByteBuffer.wrap(binary);
        bb.putShort(4, (short) (binary.length - 7)); // write packet length
        seqFiller.fill(binary); //write sequence count

        pc.setBinary(binary);

        if (hasCrc) {
            int pos = binary.length - 2;
            try {
                int checkword = errorDetectionCalculator.compute(binary, 0, pos);
                log.debug("Appending checkword on position {}: {}", pos, Integer.toHexString(checkword));
                bb.putShort(pos, (short) checkword);
                pc.setBinary(bb.array());

            } catch (IllegalArgumentException e) {
                log.warn("Error when computing checkword: " + e.getMessage());
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

    public boolean timetagSanityCheck(long timetag) {
        if (timetag == 0)
            return true;

        if (timetag < 0)
            return false;
        
        if (Instant.now()
            .plusSeconds(timetagBuffer)
            .atZone(ZoneId.of("GMT"))
            .isAfter(
                Instant.ofEpochMilli(timetag)
                .atZone(ZoneId.of("GMT"))
            )
        )
            return false;

        return true;
    }

    /** Send to command history the failed command */
    protected void failedCommand(CommandId commandId, String reason) {
        log.debug("Failing command {}: {}", commandId, reason);
        long currentTime = getCurrentTime();

        commandHistoryListener.publishAck(commandId, CommandHistoryPublisher.AcknowledgeSent_KEY, currentTime, AckStatus.NOK, reason);
        commandHistoryListener.commandFailed(commandId, currentTime, reason);
    }

    @Override
    public byte[] process(PreparedCommand pc) {
        pc = pusTcManager.addPusModifiers(pc);
        boolean timetagSanity = timetagSanityCheck(pc.getTimetag());
        if (!timetagSanity) {
            failedCommand(pc.getCommandId(), "Failed due to complications from an incorrect Timetag value provided. Please check the logs for more details");
            return null;
        }
        partialProcess(pc);

        if (timetagSanity && pc.getTimetag() != 0) {
            byte[] preBinary = pc.getBinary();

            int apid = seqFiller.getApid(preBinary);
            int ccsdsSeqCount = seqFiller.getCcsdsSeqCount(preBinary);
            long timetag = pc.getTimetag(); // This will be UNIX timestamp
            LocalDateTime localTimetag = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timetag),
                ZoneId.of("GMT")
            );

            pc = pusTcManager.addTimetagModifiers(pc);
            partialProcess(pc);

            commandHistoryListener.publish(pc.getCommandId(), CommandHistoryPublisher.Timetag_KEY, localTimetag.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss' UTC'")));
            commandHistoryListener.publish(pc.getCommandId(), CommandHistoryPublisher.Timetagged_CommandApid_KEY, apid);
            commandHistoryListener.publish(pc.getCommandId(), CommandHistoryPublisher.Timetagged_CommandCcsdsSeq_KEY, ccsdsSeqCount);
            commandHistoryListener.publish(pc.getCommandId(), CommandHistoryPublisher.Timetagged_Command_KEY, preBinary);
        }

        byte[] binary = pc.getBinary();

        commandHistoryListener.publish(pc.getCommandId(), CommandHistoryPublisher.CcsdsSeq_KEY, seqFiller.getCcsdsSeqCount(binary));
        commandHistoryListener.publish(pc.getCommandId(), CommandHistoryPublisher.Apid_KEY, seqFiller.getApid(binary));
        commandHistoryListener.publish(pc.getCommandId(), CommandHistoryPublisher.SourceID_KEY, pusTcManager.getSourceID());

        commandHistoryListener.publish(pc.getCommandId(), PreparedCommand.CNAME_BINARY, binary);
        return pc.getBinary();
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

    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
        this.commandHistoryListener = commandHistoryListener;
    }
}
