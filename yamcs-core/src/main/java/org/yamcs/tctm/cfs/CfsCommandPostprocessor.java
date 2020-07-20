package org.yamcs.tctm.cfs;

import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.CcsdsSeqCountFiller;
import org.yamcs.tctm.CommandPostprocessor;
import org.yamcs.utils.ByteArrayUtils;

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
    final String yamcsInstance;
    private boolean swapChecksumFc = false;

    public CfsCommandPostprocessor(String yamcsInstance) {
        this.yamcsInstance = yamcsInstance;
    }

    public CfsCommandPostprocessor(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.swapChecksumFc = config.getBoolean("swapChecksumFc", false);
    }

    @Override
    public byte[] process(PreparedCommand pc) {
        byte[] binary = pc.getBinary();
        ByteArrayUtils.encodeShort(binary.length - 7, binary, 4);// set packet length
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
