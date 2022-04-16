package org.yamcs.tctm.ccsds.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class Crc32Test {

    @Test
    public void test1() {
        byte[] data = { 0x12, 0x34, 0x56, 0x78 };

        ProximityCrc32 c = new ProximityCrc32();
        assertEquals(0x34D74CB3, c.compute(data, 0, data.length));
    }
}
