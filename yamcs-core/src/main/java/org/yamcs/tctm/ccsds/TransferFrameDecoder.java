package org.yamcs.tctm.ccsds;

import org.yamcs.tctm.TcTmException;

/**
 * Decodes raw frame data into a transfer frame of one of three CCSDS types AOS, TM or USLP
 *
 */
public interface TransferFrameDecoder {
    enum CcsdsFrameType {
        /**
         * CCSDS 732.0-B-3 
         */
        AOS(1),
        /**
         * CCSDS 132.0-B-2 
         */
        TM(0), 
        /**
         * CCSDS 732.1-B-1 
         */
        USLP(12); 
    
        private final int version;
        CcsdsFrameType(int version) {
            this.version = version;
        }
        public int getVersion() {
            return version;
        }
    }
    /**
     * Parse frame data
     * @param data - byte array representing the data
     * @param offset - where in the byte array the frame data starts
     * @param length - the length of the frame data in bytes
     * @return
     * @throws TcTmException
     */
    DownlinkTransferFrame decode(byte[] data, int offset, int length) throws TcTmException;
}
