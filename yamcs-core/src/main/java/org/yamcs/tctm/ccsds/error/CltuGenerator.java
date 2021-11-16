package org.yamcs.tctm.ccsds.error;


/**
 *  Makes CLTUs from command transfer frames as per 
 *  CCSDS 231.0-B-3 (TC SYNCHRONIZATION AND CHANNEL CODING)
 *  
 *  <p>
 *  TODO: implement randomization
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

    public abstract byte[] makeCltu(byte[] data);
}
