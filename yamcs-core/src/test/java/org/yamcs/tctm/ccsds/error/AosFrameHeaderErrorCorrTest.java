package org.yamcs.tctm.ccsds.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.yamcs.rs.ReedSolomonException;
import org.yamcs.tctm.ccsds.error.AosFrameHeaderErrorCorr.DecoderResult;

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

    @Test
    public void testUncorrecable() {
        assertThrows(ReedSolomonException.class, () -> {
            DecoderResult dr = AosFrameHeaderErrorCorr.decode(0x1230, 0x00, 0x94DC);
            checkEqual(0x1234, 0x56, dr);
        });
    }

    private void checkEqual(int vcid, int sig, DecoderResult dr) {
        assertEquals(vcid, dr.gvcid);
        assertEquals(sig, dr.signalingField);
    }
}
