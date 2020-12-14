package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;

/**
 * PUS time used by the simulator is 4 bytes seconds and 4 bytessub-second
 * 
 * The time is started at 0 when the simulator starts and drifts with a constant drift
 * 
 * @author nm
 *
 */
public class PusTime {
    public static final int LENGTH_BYTES = 8;
    static final long NANOS_IN_SEC = 1000_000_000l;
    static final long MAX_FRACTIONAL_PART = 0xFFFFFFl;
    static final byte TIME_PFIELD = (byte) 0x2F;

    static double drift = 1 + 1e-7;
    static long t0 = System.nanoTime();

    final int seconds;
    // 4 bytes unsigned
    final long fractionalTime;

    public PusTime(int seconds, long fractionalTime) {
        this.seconds = seconds;
        this.fractionalTime = fractionalTime;
    }

    public void encode(ByteBuffer bb) {
        bb.put(TIME_PFIELD);
        bb.putInt(seconds);
        bb.put((byte) (fractionalTime >> 16));
        bb.putShort((short) (fractionalTime & 0xFFFF));

    }

    public static PusTime now() {
        long nanos = System.nanoTime() - t0;
        int sec = (int) (nanos / NANOS_IN_SEC);
        double fine = drift * ((nanos % NANOS_IN_SEC) / (double) NANOS_IN_SEC);
        if (fine > 1) {
            sec++;
            fine -= 1;
        }
        long fractionalTime = (long) (fine * MAX_FRACTIONAL_PART);
        return new PusTime(sec, fractionalTime);
    }

    @Override
    public String toString() {
        return "PusTime [seconds=" + seconds + ", fractionalTime=" + fractionalTime + "]";
    }
}
