package org.yamcs.tctm.cfs;

import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent_KEY;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.CcsdsSeqCountFiller;
import org.yamcs.tctm.CommandPostprocessor;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;

/**
 * CFS TC packets:
 * <ul>
 * <li>CCSDS primary header - 6 bytes. Should be set according to CCSDS 133.0-B.</li>
 * <li>function code - 1 byte</li>
 * <li>checksum - 1 byte</li>
 * </ul>
 * The checksum is an XOR of all bytes from the packet with the initial checksum set to 0.
 * 
 * <p>
 * This class sets the CCSDS sequence count and packet length in the primary CCSDS header and the checksum in the
 * secondary CCSDS header.
 * <p>
 * The other parts of the header/packet are expected to be set by the command composition according to the Mission
 * Database.
 * <p>
 * Note that prior to
 * <a href="https://github.com/nasa/cFE/pull/586/commits/ff3aa947bbd146747707f2ae13acfe3a30eb9e0a"> this patch</a>
 * the cFS would expect the checksum and the function code swapped on little endian systems. The configuration option
 * swapChecksumFc can be used to realize this behaviour:
 * 
 * <pre>
 *  dataLinks: 
 *  ...
 *       commandPostprocessorClassName: org.yamcs.tctm.cfs.CfsCommandPostprocessor
 *       commandPostprocessorArgs:
 *           swapChecksumFc: true
 *
 * </pre>
 * 
 * @author nm
 *
 */
public class CfsCommandPostprocessor implements CommandPostprocessor {
    protected CcsdsSeqCountFiller seqFiller = new CcsdsSeqCountFiller();
    protected CommandHistoryPublisher commandHistoryPublisher;
    final static int CHECKSUM_OFFSET = 7;
    final static int FC_OFFSET = 6;
    final static int MIN_CMD_LENGTH = 7;
    String yamcsInstance;
    private boolean swapChecksumFc = false;
    static Logger log = LoggerFactory.getLogger(CfsCommandPostprocessor.class);
    
    public void init(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.swapChecksumFc = config.getBoolean("swapChecksumFc", false);
    }

    @Override
    public byte[] process(PreparedCommand pc) {
        byte[] binary = pc.getBinary();
        if(binary.length < MIN_CMD_LENGTH) {
            String msg = ("Short command received, length:"+binary.length+", expected minimum length: "+MIN_CMD_LENGTH);
            log.warn(msg);
            long t = TimeEncoding.getWallclockTime();
            commandHistoryPublisher.publishAck(pc.getCommandId(), AcknowledgeSent_KEY, t, AckStatus.NOK, msg);
            commandHistoryPublisher.commandFailed(pc.getCommandId(), t, msg);
            return null;
        }
        
        ByteArrayUtils.encodeUnsignedShort(binary.length - 7, binary, 4);// set packet length
        int seqCount = seqFiller.fill(binary);
        commandHistoryPublisher.publish(pc.getCommandId(), CommandHistoryPublisher.CcsdsSeq_KEY, seqCount);

        // set the checksum
        binary[CHECKSUM_OFFSET] = 0;
        int checksum = 0xFF;
        for (int i = 0; i < binary.length; i++) {
            checksum = checksum ^ binary[i];
        }
        binary[CHECKSUM_OFFSET] = (byte) checksum;
        if (swapChecksumFc) {
            byte x = binary[CHECKSUM_OFFSET];
            binary[CHECKSUM_OFFSET] = binary[FC_OFFSET];
            binary[FC_OFFSET] = x;
        }
        commandHistoryPublisher.publish(pc.getCommandId(), PreparedCommand.CNAME_BINARY, binary);
        return binary;
    }

    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
        this.commandHistoryPublisher = commandHistoryListener;
    }
}
