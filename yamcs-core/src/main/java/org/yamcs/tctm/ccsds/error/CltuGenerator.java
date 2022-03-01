package org.yamcs.tctm.ccsds.error;


/**
 *  Makes CLTUs from command transfer frames as per 
 *  CCSDS 231.0-B-3 (TC SYNCHRONIZATION AND CHANNEL CODING)
 *  
 */
public abstract class CltuGenerator {
    public final static byte[] EMPTY_SEQ = {};
    protected final byte[] startSeq;
    protected final byte[] tailSeq;
    
    public enum Encoding {
        BCH, LDCP64, LDPC256, CUSTOM
    };

    public CltuGenerator(byte[] startSeq, byte[] tailSeq) {
        this.startSeq = startSeq;
        this.tailSeq = tailSeq;
    }

    /**
     * encode the data optionally randomizing it. Note that randomization is mandatory for the LDCP codec so that codec
     * will throw an IllegalArgumentException if the argument is false.
     *
     * @param -
     *            data to be encoded
     * @param -
     *            randomize. If true the data will be randomized before (BCH) or after (LPDC) encoding
     * @return - the encoded data
     */
    public abstract byte[] makeCltu(byte[] data, boolean randomize);
}
