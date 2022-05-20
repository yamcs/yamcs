package org.yamcs.tctm.ccsds;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class RandomizerTest {

    @Test
    public void testTc() {
        // System.out.println(StringConverter.arrayToHexString(Randomizer.tcseq, false));
        String ccsdsRefSeq = "1111 1111 0011 1001 1001 1110 0101 1010 0110 1000";
        assertEquals(ccsdsRefSeq, toBinaryString(Randomizer.tcseq, 5));
    }

    String toBinaryString(byte[] a, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            int x = a[i] & 0xFF;
            sb.append(fourbitsToString(x >> 4)).append(" ").append(fourbitsToString(x & 0xF));
            if (i != n - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    String fourbitsToString(int x) {
        return String.format("%4s", Integer.toBinaryString(x)).replace(' ', '0');
    }
}
