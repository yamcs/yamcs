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
import org.yamcs.tctm.pus.services.tc.PusTcCcsdsPacket;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;

public class PusCommandPostprocessor implements CommandPostprocessor {
    static Logger log = LoggerFactory.getLogger(PusCommandPostprocessor.class);
    
    protected CommandHistoryPublisher commandHistoryListener;
    protected TimeService timeService;

    public PusCommandPostprocessor(String yamcsInstance) {
        this(yamcsInstance, null);
    }

    public PusCommandPostprocessor(String yamcsInstance, YConfiguration config) {
        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    protected long getCurrentTime() {
        if (timeService != null) {
            return timeService.getMissionTime();

        } else {
            return TimeEncoding.getWallclockTime();
        }
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
        long timetag = pc.getTimestampAttribute(CommandHistoryPublisher.Timetag_KEY);
        
        boolean timetagSanity = PusTcManager.timetagSanityCheck(timetag);
        if (!timetagSanity) {
            failedCommand(pc.getCommandId(), "Failed due to complications from an incorrect Timetag value provided. Please check the logs for more details");
            return null;
        }

        // If timetag != 0, then it is an actual timetag command
        if (timetag != 0) {
            if (PusTcManager.timetagResolution == PusTcManager.TimetagResolution.SECOND) {
                timetag *= 1000;
            }
            LocalDateTime localTimetag = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timetag),
                ZoneId.of("GMT")
            );

            commandHistoryListener.publish(
                pc.getCommandId(),
                CommandHistoryPublisher.Timetag_KEY,
                localTimetag.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss' UTC'"))
            );
            commandHistoryListener.publish(
                pc.getCommandId(),
                CommandHistoryPublisher.Timetagged_CommandApid_KEY,
                pc.getSignedIntegerAttribute(CommandHistoryPublisher.Timetagged_CommandApid_KEY)
            );
            commandHistoryListener.publish(
                pc.getCommandId(),
                CommandHistoryPublisher.Timetagged_CommandCcsdsSeq_KEY,
                pc.getSignedIntegerAttribute(CommandHistoryPublisher.Timetagged_CommandCcsdsSeq_KEY)
            );
            commandHistoryListener.publish(
                pc.getCommandId(),
                CommandHistoryPublisher.Timetagged_Command_KEY,
                pc.getBinaryAttribute(CommandHistoryPublisher.Timetagged_Command_KEY)
            );
        }

        commandHistoryListener.publish(
            pc.getCommandId(),
            CommandHistoryPublisher.CcsdsSeq_KEY,
            PusTcCcsdsPacket.getSequenceCount(pc.getBinary())
        );
        commandHistoryListener.publish(
            pc.getCommandId(),
            CommandHistoryPublisher.Apid_KEY,
            PusTcCcsdsPacket.getAPID(pc.getBinary())
        );
        commandHistoryListener.publish(
            pc.getCommandId(),
            CommandHistoryPublisher.SourceId_KEY,
            PusTcManager.sourceId
        );
        commandHistoryListener.publish(
            pc.getCommandId(),
            PreparedCommand.CNAME_BINARY,
            pc.getBinary()
        );

        return pc.getBinary();
    }

    @Override
    public int getBinaryLength(PreparedCommand pc) {
        return pc.getBinary().length;
    }

    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
        this.commandHistoryListener = commandHistoryListener;
    }
}
