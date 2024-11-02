package org.yamcs.tctm;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent_KEY;
import static org.yamcs.tctm.AbstractPacketPreprocessor.CONFIG_KEY_ERROR_DETECTION;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.utils.GpsCcsdsTime;
import org.yamcs.utils.TimeEncoding;

public class IssCommandPostprocessor implements CommandPostprocessor {
    static Logger log = LoggerFactory.getLogger(IssCommandPostprocessor.class);

    protected int minimumTcPacketLength = -1; // the minimum size of the CCSDS packets uplinked
    protected int maximumTcPacketLength = -1; // the maximum size of the CCSDS packets uplinked
    ErrorDetectionWordCalculator errorDetectionCalculator;
    protected CcsdsSeqCountFiller seqFiller = new CcsdsSeqCountFiller();

    protected CommandHistoryPublisher commandHistory;
    boolean enforceEvenNumberOfBytes;

    public void init(String yamcsInstance, YConfiguration config) {
        minimumTcPacketLength = config.getInt("minimumTcPacketLength", -1);
        maximumTcPacketLength = config.getInt("maximumTcPacketLength", -1);
        enforceEvenNumberOfBytes = config.getBoolean("enforceEvenNumberOfBytes", false);
        if (config.containsKey(CONFIG_KEY_ERROR_DETECTION)) {
            errorDetectionCalculator = AbstractPacketPreprocessor.getErrorDetectionWordCalculator(config);
        } else {
            errorDetectionCalculator = new Running16BitChecksumCalculator();
        }
    }

    @Override
    public byte[] process(PreparedCommand pc) {
        byte[] binary = pc.getBinary();
        boolean secHeaderFlag = CcsdsPacket.getSecondaryHeaderFlag(binary);
        boolean checksumIndicator = false;
        if (secHeaderFlag) {
            checksumIndicator = CcsdsPacket.getChecksumIndicator(binary);
        }
        int newLength = getBinaryLength(pc);
        if (maximumTcPacketLength != -1 && newLength > maximumTcPacketLength) {
            String msg = "Command too long, length:" + newLength + ", expected maximum length: "
                    + maximumTcPacketLength;
            log.warn(msg);
            long t = TimeEncoding.getWallclockTime();
            commandHistory.publishAck(pc.getCommandId(), AcknowledgeSent_KEY, t, AckStatus.NOK, msg);
            commandHistory.commandFailed(pc.getCommandId(), t, msg);
            return null;
        }
        if (newLength > binary.length) {
            binary = Arrays.copyOf(binary, newLength);
        }
        ByteBuffer bb = ByteBuffer.wrap(binary);
        bb.putShort(4, (short) (binary.length - 7)); // fix packet length
        int seqCount = seqFiller.fill(binary);

        if (secHeaderFlag) {
            GpsCcsdsTime gpsTime = TimeEncoding.toGpsTime(pc.getCommandId().getGenerationTime());
            bb.putInt(6, gpsTime.coarseTime);
            bb.put(10, gpsTime.fineTime);
        }

        commandHistory.publish(pc.getCommandId(), CommandHistoryPublisher.CcsdsSeq_KEY, seqCount);
        if (checksumIndicator) {
            int pos = binary.length - 2;
            try {
                int checkword = errorDetectionCalculator.compute(binary, 0, pos);
                log.debug("Appending checkword on position {}: {}", pos, Integer.toHexString(checkword));
                bb.putShort(pos, (short) checkword);
            } catch (IllegalArgumentException e) {
                log.warn("Error when computing checkword: " + e.getMessage());
            }
        } else {
            if (!secHeaderFlag) {
                log.debug(
                        "Not appending a checkword since there is no secondary header to configure a checksum indicator");
            } else {
                log.debug("Not appending a checkword since checksumIndicator is false");
            }
        }

        commandHistory.publish(pc.getCommandId(), PreparedCommand.CNAME_BINARY, binary);
        return binary;
    }

    @Override
    public int getBinaryLength(PreparedCommand pc) {
        byte[] binary = pc.getBinary();
        int length = binary.length;
        boolean secHeaderFlag = CcsdsPacket.getSecondaryHeaderFlag(binary);
        boolean checksumIndicator = false;
        if (secHeaderFlag) {
            checksumIndicator = CcsdsPacket.getChecksumIndicator(binary);
        }

        if (checksumIndicator) { // 2 extra bytes for the checkword
            length += 2;
        }

        if (length < minimumTcPacketLength) { // enforce the minimum packet length
            length = minimumTcPacketLength;
        }
        if (enforceEvenNumberOfBytes && (length & 1) == 1) {
            length += 1;
        }
        return length;
    }

    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
        this.commandHistory = commandHistoryListener;
    }

    public int getMinimumTcPacketLength() {
        return minimumTcPacketLength;
    }

    public int getMaximumTcPacketLength() {
        return maximumTcPacketLength;
    }
}
