package org.yamcs.tctm.ccsds.error;

import org.yamcs.tctm.ccsds.Randomizer;

/**
 *  Makes CLTUs from command transfer frames as per 
 *  CCSDS 231.0-B-3 (TC SYNCHRONIZATION AND CHANNEL CODING)
 *  
 *  <p>
 *  TODO: implement randomization
 */
public abstract class CltuGenerator {
    public final static byte[] EMPTY_SEQ = {};
    
    public enum Encoding {
        BCH, LDCP64, LDPC256
    };
    final protected boolean randomize;

    public CltuGenerator(boolean randomize) {
        this.randomize = randomize;
    }
    
    public void randomize(byte[] data) {
        Randomizer.randomizeTc(data);
    }
    
    public abstract byte[] makeCltu(byte[] data);
}
