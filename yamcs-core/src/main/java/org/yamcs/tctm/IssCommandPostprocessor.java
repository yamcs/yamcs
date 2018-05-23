package org.yamcs.tctm;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.utils.CcsdsPacket;

public class IssCommandPostprocessor implements CommandPostprocessor {
    static Logger log = LoggerFactory.getLogger(IssCommandPostprocessor.class);
    
    protected int minimumTcPacketLength = -1; // the minimum size of the CCSDS packets uplinked
    final ErrorDetectionWordCalculator errorDetectionCalculator;
    protected CcsdsSeqCountFiller seqFiller = new CcsdsSeqCountFiller();
    static final String CONFIG_KEY_ERROR_DETECTION = "errorDetection";
    protected CommandHistoryPublisher commandHistoryListener;
    boolean enforceEvenNumberOfBytes;
    
    public IssCommandPostprocessor(String yamcsInstance) {
        errorDetectionCalculator = new Running16BitChecksumCalculator();
    }
    
    public IssCommandPostprocessor(String yamcsInstance, Map<String, Object> config) {
        minimumTcPacketLength = YConfiguration.getInt(config, "minimumTcPacketLength", -1);
        enforceEvenNumberOfBytes = YConfiguration.getBoolean(config, "enforceEvenNumberOfBytes", false);
        if(config.containsKey(CONFIG_KEY_ERROR_DETECTION)) {
            Map<String, Object> c = YConfiguration.getMap(config, CONFIG_KEY_ERROR_DETECTION);
            String type = YConfiguration.getString(c, "type");
            if("16-SUM".equalsIgnoreCase(type)) {
                errorDetectionCalculator = new Running16BitChecksumCalculator();
            } else if("CRC-16-CCIIT".equalsIgnoreCase(type)) {
                errorDetectionCalculator = new CrcCciitCalculator(c);
            } else {
                throw new ConfigurationException("Unknown errorDetectionWord type '"+type+"': supported types are 16-SUM and CRC-16-CCIIT");
            }
        } else {
            errorDetectionCalculator = new Running16BitChecksumCalculator();
        }
    }

    @Override
    public byte[] process(PreparedCommand pc) {
        byte [] binary = pc.getBinary();
        boolean secHeaderFlag = CcsdsPacket.getSecondaryHeaderFlag(binary);
        boolean checksumIndicator = false;
        if(secHeaderFlag) {
            checksumIndicator = CcsdsPacket.getChecksumIndicator(binary);
        }
        
        int newLength = binary.length;
        if (checksumIndicator) { //2 extra bytes for the checkword
            newLength+=2;
            binary = Arrays.copyOf(binary, binary.length+2);
        }
        
        if (newLength < minimumTcPacketLength) { // enforce the minimum packet length
            newLength = minimumTcPacketLength;
        }
        if(enforceEvenNumberOfBytes && (newLength & 1) == 1) {
            newLength+=1;
        }
        
        if(newLength>binary.length) {
            binary = Arrays.copyOf(binary, newLength);
        }
        ByteBuffer bb = ByteBuffer.wrap(binary);
        bb.putShort(4, (short) (binary.length - 7)); // fix packet length
       
        int seqCount = seqFiller.fill(bb, pc.getCommandId().getGenerationTime());
        commandHistoryListener.publish(pc.getCommandId(), "ccsds-seqcount", seqCount);
        if (checksumIndicator) { 
            int pos = binary.length - 2;
            try {
                int checkword = errorDetectionCalculator.compute(binary, 0, pos);
                log.debug("Appending checkword on position {}: {}", pos, Integer.toHexString(checkword));
                bb.putShort(pos, (short)checkword);
            } catch (IllegalArgumentException e) {
                log.warn("Error when computing checkword: "+e.getMessage());
            }
        } else {
            if(!secHeaderFlag) {
                log.debug("Not appending a checkword since there is no secondary header to configure a checksum indicator");
            } else {
                log.debug("Not appending a checkword since checksumIndicator is false");
            }
        }
    
        return binary;
    }
    
    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
        this.commandHistoryListener = commandHistoryListener;
    }


    public int getMiniminimumTcPacketLength() {
        return minimumTcPacketLength;
    }
}
