package org.yamcs.tctm.ccsds.error;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.yamcs.tctm.ccsds.error.BitMatrix.add;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.yamcs.tctm.ccsds.error.Ldpc256CltuGenerator.Ldpc256Encoder;
import org.yamcs.tctm.ccsds.error.Ldpc64CltuGenerator.Ldpc64Encoder;
import org.yamcs.utils.ByteArrayUtils;

public class LdpcEncoderTest {
    static BitMatrix h64;
    static BitMatrix h256;

    @BeforeAll
    public static void buildH64() {
        BitMatrix im = BitMatrix.IdentityMatrix(16);
        BitMatrix zm = BitMatrix.ZeroMatrix(16);
        BitMatrix p0 = im;
        BitMatrix p1 = im.circularRightShift(1);
        BitMatrix p2 = im.circularRightShift(2);
        BitMatrix p3 = im.circularRightShift(3);
        BitMatrix p4 = im.circularRightShift(4);
        BitMatrix p6 = im.circularRightShift(6);
        BitMatrix p7 = im.circularRightShift(7);
        BitMatrix p9 = im.circularRightShift(9);
        BitMatrix p11 = im.circularRightShift(11);
        BitMatrix p13 = im.circularRightShift(13);
        BitMatrix p14 = im.circularRightShift(14);
        BitMatrix p15 = im.circularRightShift(15);

        h64 = BitMatrix.compose(new BitMatrix[][] {
                { add(im, p7), p2, p14, p6, zm, p0, p13, im },
                { p6, add(im, p15), p0, p1, im, zm, p0, p7 },
                { p4, p1, add(im, p15), p14, p11, im, zm, p3 },
                { p0, p1, p9, add(im, p13), p14, p1, im, zm }
        });

        // System.out.println("h64: "+h64);
    }

    @BeforeAll
    public static void buildH256() {
        BitMatrix zm = BitMatrix.ZeroMatrix(64);
        BitMatrix im = BitMatrix.IdentityMatrix(64);
        BitMatrix p0 = im;
        BitMatrix p3 = im.circularRightShift(3);
        BitMatrix p11 = im.circularRightShift(11);
        BitMatrix p16 = im.circularRightShift(16);
        BitMatrix p23 = im.circularRightShift(23);
        BitMatrix p25 = im.circularRightShift(25);
        BitMatrix p26 = im.circularRightShift(26);
        BitMatrix p27 = im.circularRightShift(27);
        BitMatrix p30 = im.circularRightShift(30);
        BitMatrix p35 = im.circularRightShift(35);
        BitMatrix p37 = im.circularRightShift(37);
        BitMatrix p43 = im.circularRightShift(43);
        BitMatrix p50 = im.circularRightShift(50);
        BitMatrix p55 = im.circularRightShift(55);
        BitMatrix p56 = im.circularRightShift(56);
        BitMatrix p58 = im.circularRightShift(58);
        BitMatrix p61 = im.circularRightShift(61);
        BitMatrix p62 = im.circularRightShift(62);
        BitMatrix p63 = im.circularRightShift(63);

        h256 = BitMatrix.compose(new BitMatrix[][] {
                { add(im, p63), p30, p50, p25, zm, p43, p62, im },
                { p56, add(im, p61), p50, p23, im, zm, p37, p26 },
                { p16, p0, add(im, p55), p27, p56, im, zm, p43 },
                { p35, p56, p62, add(im, p11), p58, p3, im, zm }
        });

    }

    @Test
    public void testRotrby16() {
        assertEquals(0x0800_1000_8000_0000L, Ldpc64Encoder.rotrGroupOf16(0x1000_2000_0001_0000L));
    }

    @Test
    public void test64() {
        byte[] d = new byte[] { 8, 0, 5, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0 };
        Ldpc64Encoder.encode(d, 0, d, 8);

        byte[] zero = new byte[8];
        assertArrayEquals(zero, h64.multiply(d));

        Random r = new Random();
        r.nextBytes(d);

        Ldpc64Encoder.encode(d, 0, d, 8);
        assertArrayEquals(zero, h64.multiply(d), "failing input data: " + Arrays.toString(d));

    }

    @Test
    @Disabled
    public void test64Speed() {
        int n = 10_000_000;
        long c = 0;
        long t0 = System.currentTimeMillis();
        byte[] d = new byte[] { 8, 0, 5, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0 };

        for (int i = 0; i < n; i++) {
            ByteArrayUtils.encodeLong(i, d, 0);
            Ldpc64Encoder.encode(d, 0, d, 8);
            c += d[8];
        }
        long t1 = System.currentTimeMillis();

        long delta = t1 - t0;
        System.out.println(c + " time: " + delta / 1000 + " sec speed " + delta * 1000_000.0 / n + " nanosec/ops, "
                + (n * 8 * 1000.0) / (delta * 1024 * 1024) + " MBps");
    }

    @Test
    @Disabled
    public void test256Speed() {
        int n = 10_000_000;
        long c = 0;
        long t0 = System.currentTimeMillis();
        byte[] d = new byte[64];

        for (int i = 0; i < n; i++) {
            ByteArrayUtils.encodeLong(i, d, 0);
            Ldpc256Encoder.encode(d, 0, d, 8);
            c += d[32];
        }
        long t1 = System.currentTimeMillis();

        long delta = t1 - t0;
        System.out.println(c + " time: " + delta / 1000 + " sec speed: " + delta * 1000_000.0 / n + " nanosec/ops, "
                + (n * 32 * 1000.0) / (delta * 1024 * 1024) + " MBps");
    }

    @Test
    public void test256() {
        byte[] d = new byte[64];
        d[31] = 1;
        Ldpc256Encoder.encode(d, 0, d, 32);
        byte[] zero = new byte[32];
        assertArrayEquals(zero, h256.multiply(d));

        Random r = new Random();
        r.nextBytes(d);

        Ldpc256Encoder.encode(d, 0, d, 32);
        assertArrayEquals(zero, h256.multiply(d), "failing input data: " + Arrays.toString(d));
    }
}
