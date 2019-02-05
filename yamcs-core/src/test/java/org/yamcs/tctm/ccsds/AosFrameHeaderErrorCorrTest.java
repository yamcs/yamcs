package org.yamcs.tctm.ccsds;

import static org.junit.Assert.assertEquals;


import org.junit.Test;
import org.yamcs.rs.ReedSolomonException;
import org.yamcs.tctm.ccsds.AosFrameHeaderErrorCorr.DecoderResult;

public class AosFrameHeaderErrorCorrTest {
  
    @Test
    public void testEncode() {
        assertEquals(0x94DC, AosFrameHeaderErrorCorr.encode(0x1234, 0x56));
        
        assertEquals(0x457C, AosFrameHeaderErrorCorr.encode(0x369C, 0xFA));
    }
    
    @Test
    public void testDecode() throws ReedSolomonException {
        DecoderResult dr = AosFrameHeaderErrorCorr.decode(0x1234, 0x56, 0x94DC);
        checkEqual(0x1234, 0x56, dr);
        
        dr = AosFrameHeaderErrorCorr.decode(0x1234, 0x56, 0x9400);
        checkEqual(0x1234, 0x56, dr);
        
        dr = AosFrameHeaderErrorCorr.decode(0x1211, 0x56, 0x94DC);
        checkEqual(0x1234, 0x56, dr);
        
        dr = AosFrameHeaderErrorCorr.decode(0x1234, 0xAA, 0x94DC);
        checkEqual(0x1234, 0x56, dr);

    }
    
    
    @Test(expected = ReedSolomonException.class)
    public void testUncorrecable() throws ReedSolomonException {
        DecoderResult dr = AosFrameHeaderErrorCorr.decode(0x1230, 0x00, 0x94DC);
        checkEqual(0x1234, 0x56, dr);
    }

    private void checkEqual(int vcid, int sig, DecoderResult dr) {
        assertEquals(vcid, dr.vcid);
        assertEquals(sig, dr.signalingField);
    }
    
    
}
