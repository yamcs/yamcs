package org.yamcs.tctm.cfs;

import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.CcsdsSeqCountFiller;
import org.yamcs.tctm.CommandPostprocessor;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.StringConverter;

/**
 * CFS TC packets:
 * <ul>
 * <li>CCSDS primary header - 6 bytes</li>
 * <li>function code - 1 byte</li>
 * <li> checksum - 1 byte</li>
 * </ul>
 * 
 * This class sets the CCSDS packet length and the checksum.
 * 
 * @author nm
 *
 */
public class CfsCommandPostprocessor implements CommandPostprocessor {
    protected CcsdsSeqCountFiller seqFiller = new CcsdsSeqCountFiller();
    protected CommandHistoryPublisher commandHistoryPublisher;
    final static int CHECKSUM_OFFSET = 6;
    final String    yamcsInstance;
    
    public CfsCommandPostprocessor(String yamcsInstance) {
        this.yamcsInstance = yamcsInstance;
    }
    
    
    @Override
    public byte[] process(PreparedCommand pc) {
        
        byte[] binary = pc.getBinary();
        System.out.println("got binary "+StringConverter.arrayToHexString(binary));
        
        
        ByteArrayUtils.encodeShort(binary.length - 7, binary, 4);// set packet length
        int seqCount = seqFiller.fill(binary);
        commandHistoryPublisher.publish(pc.getCommandId(), "ccsds-seqcount", seqCount);
       
        
        //set the checksum
        binary[CHECKSUM_OFFSET] =0;
        int checksum = 0xFF;
        for(int i=0; i<binary.length; i++) {
            checksum = checksum ^ binary[i];
        }
        binary[CHECKSUM_OFFSET] = (byte) checksum;
        
        commandHistoryPublisher.publish(pc.getCommandId(), PreparedCommand.CNAME_BINARY, binary);
        System.out.println("sending binary "+StringConverter.arrayToHexString(binary));
        return binary;
    }
    
    
    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
        this.commandHistoryPublisher = commandHistoryListener;
    }
}
