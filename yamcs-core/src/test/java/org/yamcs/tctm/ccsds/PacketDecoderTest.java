package org.yamcs.tctm.ccsds;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.tctm.TcTmException;
import org.yamcs.utils.StringConverter;

public class PacketDecoderTest {
    List<byte[]> pl = new ArrayList<>();
    PacketDecoder pd = new PacketDecoder(1000, (byte[] p) -> pl.add(p));

    @BeforeEach
    public void emptyList() {
        pl.clear();
        pd.skipIdlePackets(false);
        pd.stripEncapsulationHeader(false);
    }

    @Test
    public void testOneByteEncapsulation() throws TcTmException {
        pd.process(new byte[] { (byte) 0xE0 }, 0, 1);
        assertFalse(pd.hasIncompletePacket());
        assertEquals(1, pl.size());
        byte[] p = pl.get(0);
        assertArrayEquals(new byte[] { (byte) 0xE0 }, p);
    }

    @Test
    public void testOneByteIdle() throws TcTmException {
        pd.skipIdlePackets(true);
        pd.process(new byte[] { (byte) 0xE0 }, 0, 1);
        assertFalse(pd.hasIncompletePacket());
        assertEquals(0, pl.size());
    }

    @Test
    public void testOneByteNoEncapsulationHeader() throws TcTmException {
        pd.stripEncapsulationHeader(true);
        pd.process(new byte[] { (byte) 0xE0 }, 0, 1);
        assertFalse(pd.hasIncompletePacket());
        assertEquals(1, pl.size());
        assertEquals(0, pl.get(0).length);
    }

    @Test
    public void testTwoBytesEncapsulation() throws TcTmException {
        pd.process(new byte[] { (byte) 0xE1, 2 }, 0, 2);
        assertFalse(pd.hasIncompletePacket());
        assertEquals(1, pl.size());
        byte[] p = pl.get(0);
        assertArrayEquals(new byte[] { (byte) 0xE1, 2 }, p);
    }

    @Test
    public void testFourBytesEncapsulation() throws TcTmException {
        pd.process(new byte[] { (byte) 0xE2, 0, 0, 4 }, 0, 4);
        assertFalse(pd.hasIncompletePacket());
        assertEquals(1, pl.size());
        byte[] p = pl.get(0);
        assertArrayEquals(new byte[] { (byte) 0xE2, 0, 0, 4 }, p);

        pl.clear();
        pd.process(new byte[] { (byte) 0xE2, 0 }, 0, 2);
        assertTrue(pd.hasIncompletePacket());
        assertEquals(0, pl.size());
        pd.process(new byte[] { (byte) 0, 4 }, 0, 2);
        assertFalse(pd.hasIncompletePacket());
        p = pl.get(0);
        assertArrayEquals(new byte[] { (byte) 0xE2, 0, 0, 4 }, p);
    }

    @Test
    public void testEightBytesEncapsulation() throws TcTmException {
        pd.process(new byte[] { (byte) 0xE3, 0, 0, 0,
                0, 0, 0, 9 }, 0, 8);
        assertTrue(pd.hasIncompletePacket());
        assertEquals(0, pl.size());
        pd.process(new byte[] { 10 }, 0, 1);
        assertFalse(pd.hasIncompletePacket());
        assertEquals(1, pl.size());
        byte[] p = pl.get(0);
        assertArrayEquals(new byte[] { (byte) 0xE3, 0, 0, 0, 0, 0, 0, 9, 10 }, p);
    }

    @Test
    public void testMinCcsds() throws TcTmException {
        pd.process(new byte[] { 0, 0, 0, 0, 0 }, 0, 5);
        assertTrue(pd.hasIncompletePacket());
        assertEquals(0, pl.size());
        pd.process(new byte[] { 0 }, 0, 1);
        assertTrue(pd.hasIncompletePacket());
        assertEquals(0, pl.size());
        pd.process(new byte[] { 0 }, 0, 1);
        assertFalse(pd.hasIncompletePacket());
        assertEquals(1, pl.size());
        byte[] p = pl.get(0);
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 0 }, p);

    }

    @Test
    public void testInvalidTwoBytesEncapsulation() {
        assertThrows(TcTmException.class, () -> {
            pd.process(new byte[] { (byte) 0xE1, 1 }, 0, 2);
        });
    }

    @Test
    public void testInvalidFourBytesEncapsulation() {
        assertThrows(TcTmException.class, () -> {
            pd.process(new byte[] { (byte) 0xE2, 0, 0, 1 }, 0, 4);
            assertFalse(pd.hasIncompletePacket());
        });
    }

    @Test
    public void test1() throws TcTmException {
        String s = "6AC100000B00DD070000FE000020001A00000168E1920FBE00000058000004403FE2B7A442CAD2DDDC92FE000020001A00000168E19213A800000059000004413FE0085CE423378009CFFE000020001A00000168E19217910000005A000004423FDA6026360C2F916A9FFE000020001A00000168E1921B7A0000005B000004433FD46C1B899FD9179FAFFE000020001A00000168E1921F630000005C000004443FCC87A81DD59BA94A41FE000020001A00000168E192234C0000005D000004453FBFDC3EBECE2C50141BFE000020001A00000168E19227350000005E000004463F995EBAA84441E2CA87FE000020001A00000168E1922B1F0000005F00000447BFB33D1A94A4277A44ABE1F00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000007C00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
        byte[] p = StringConverter.hexStringToArray(s);
        pd.process(p, 10, 496);
        assertFalse(pd.hasIncompletePacket());

    }
}
