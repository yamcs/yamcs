package org.yamcs.tctm.ccsds;

import org.yamcs.tctm.TcTmException;

public interface TransferFrameDecoder {
    enum CcsdsFrameType {
        /**
         * CCSDS 732.0-B-3 
         */
        AOS,
        /**
         * CCSDS 132.0-B-2 
         */
        TM, 
        /**
         * CCSDS 732.1-B-1 
         */
        USLP}; 
    
    /**
     * Parse frame data
     * @param data - byte array representing the data
     * @param offset - where in the byte array the frame data starts
     * @param length - the length of the frame data in bytes
     * @return
     * @throws TcTmException
     */
    TransferFrame decode(byte[] data, int offset, int length) throws TcTmException;
        
}
