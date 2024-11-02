package org.yamcs.tctm.pus;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.tctm.CcsdsPacket;
import org.yamcs.tctm.CcsdsSeqCountFiller;
import org.yamcs.tctm.CommandPostprocessor;
import org.yamcs.tctm.ErrorDetectionWordCalculator;

public class PusCommandPostprocessor implements CommandPostprocessor {
    static Logger log = LoggerFactory.getLogger(PusCommandPostprocessor.class);

    ErrorDetectionWordCalculator errorDetectionCalculator;
    protected CcsdsSeqCountFiller seqFiller = new CcsdsSeqCountFiller();

    protected CommandHistoryPublisher commandHistoryListener;

    public void init(String yamcsInstance, YConfiguration config) {
        errorDetectionCalculator = AbstractPacketPreprocessor.getErrorDetectionWordCalculator(config);
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

        commandHistoryListener.publish(pc.getCommandId(), "ccsds-seqcount", seqCount);
        if (hasCrc) {
            int pos = binary.length - 2;
            try {
                int checkword = errorDetectionCalculator.compute(binary, 0, pos);
                log.debug("Appending checkword on position {}: {}", pos, Integer.toHexString(checkword));
                bb.putShort(pos, (short) checkword);
            } catch (IllegalArgumentException e) {
                log.warn("Error when computing checkword: " + e.getMessage());
            }
        }

        commandHistoryListener.publish(pc.getCommandId(), PreparedCommand.CNAME_BINARY, binary);
        return binary;
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
