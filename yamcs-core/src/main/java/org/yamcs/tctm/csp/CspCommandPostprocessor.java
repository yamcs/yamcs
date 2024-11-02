package org.yamcs.tctm.csp;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent_KEY;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32C;

import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.CommandPostprocessor;
import org.yamcs.utils.TimeEncoding;

/**
 * Link postprocessor for CSP 1.x commands (CubeSat Protocol)
 */
public class CspCommandPostprocessor implements CommandPostprocessor {
    protected Log log = new Log(getClass());

    protected int maximumTcPacketLength = -1; // the maximum size of the CSP packets uplinked
    protected CommandHistoryPublisher commandHistory;

    public void init(String yamcsInstance, YConfiguration config) {
        maximumTcPacketLength = config.getInt("maximumTcPacketLength", -1);
    }

    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistory) {
        this.commandHistory = commandHistory;
    }

    @Override
    public byte[] process(PreparedCommand pc) {
        byte[] binary = pc.getBinary();

        int newLength = getBinaryLength(pc);
        if (maximumTcPacketLength != -1 && newLength > maximumTcPacketLength) {
            String msg = "Command too long, length: " + newLength + ", expected maximum length: "
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

        if (CspPacket.getCrcFlag(binary)) {
            var crc = new CRC32C();
            crc.update(binary, 4, binary.length - 4 - 4); // Header is excluded

            var bb = ByteBuffer.wrap(binary);
            bb.putInt(binary.length - 4, (int) crc.getValue()); // uint32
        }

        commandHistory.publish(pc.getCommandId(), PreparedCommand.CNAME_BINARY, binary);
        return binary;
    }

    @Override
    public int getBinaryLength(PreparedCommand pc) {
        var binary = pc.getBinary();
        var length = binary.length;
        if (CspPacket.getCrcFlag(binary)) {
            length += 4;
        }
        return length;
    }

    public int getMaximumTcPacketLength() {
        return maximumTcPacketLength;
    }
}
