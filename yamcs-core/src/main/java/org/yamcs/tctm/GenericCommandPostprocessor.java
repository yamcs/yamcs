package org.yamcs.tctm;

import static org.yamcs.tctm.AbstractPacketPreprocessor.CONFIG_KEY_ERROR_DETECTION;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.utils.ByteArrayUtils;

public class GenericCommandPostprocessor implements CommandPostprocessor {
    static Logger log = LoggerFactory.getLogger(IssCommandPostprocessor.class);

    final ErrorDetectionWordCalculator errorDetectionCalculator;

    protected CommandHistoryPublisher commandHistoryListener;

    public GenericCommandPostprocessor(String yamcsInstance) {
        errorDetectionCalculator = new Running16BitChecksumCalculator();
    }

    public GenericCommandPostprocessor(String yamcsInstance, YConfiguration config) {
        if (config != null && config.containsKey(CONFIG_KEY_ERROR_DETECTION)) {
            errorDetectionCalculator = GenericPacketPreprocessor.getErrorDetectionWordCalculator(config);
        } else {
            errorDetectionCalculator = new Running16BitChecksumCalculator();
        }
    }

    @Override
    public byte[] process(PreparedCommand pc) {
        byte[] binary = pc.getBinary();
        if (errorDetectionCalculator != null) {
            int length = binary.length;
            int crc = errorDetectionCalculator.compute(binary, 0, length);
            int crcSizeInBits = errorDetectionCalculator.sizeInBits();
            if (crcSizeInBits == 16) {
                binary = Arrays.copyOf(binary, length + 2);
                ByteArrayUtils.encodeShort(crc, binary, length);
            } else if (crcSizeInBits == 32) {
                binary = Arrays.copyOf(binary, length + 4);
                ByteArrayUtils.encodeInt(crc, binary, length);
            } else {
                throw new IllegalArgumentException("Cannot process CRC bitsize " + crcSizeInBits);
            }
        }
        return binary;
    }

    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
        this.commandHistoryListener = commandHistoryListener;
    }

    public int getBinaryLength(PreparedCommand pc) {
        return pc.getBinary().length + (errorDetectionCalculator == null ? 0 : errorDetectionCalculator.sizeInBits()>>3);
    }
}
