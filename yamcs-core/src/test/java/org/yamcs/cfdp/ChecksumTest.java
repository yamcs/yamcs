package org.yamcs.cfdp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;
import org.python.bouncycastle.util.Arrays;
import org.yamcs.utils.StringConverter;

public class ChecksumTest {

    @Test
    public void test1() {
        byte[] data = StringConverter.hexStringToArray("000102030405060708090a0b0c0d0e");
        assertEquals(0x181C2015, ChecksumCalculator.calculateChecksum(data));
    }

    @Test
    public void test2() {
        byte[] data = StringConverter.hexStringToArray("0102030405060708090a0b0c0d0e");
        assertEquals(0x181C2015, ChecksumCalculator.calculateChecksum(data, 1, data.length));
    }

    @Test
    public void test3() {
        Random r = new Random();
        byte[] data = new byte[r.nextInt(1000)];
        r.nextBytes(data);

        long checksum1 = ChecksumCalculator.calculateChecksum(data);

        long checksum2 = 0;
        int k = 0;
        while (k < data.length) {
            int l = 1 + r.nextInt(data.length - k);
            checksum2 += ChecksumCalculator.calculateChecksum(Arrays.copyOfRange(data, k, k + l), k, l);
            k += l;
        }

        assertEquals(checksum1, checksum2 & 0xFFFFFFFFl);
    }
}
