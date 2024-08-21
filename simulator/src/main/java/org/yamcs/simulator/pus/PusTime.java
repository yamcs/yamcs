package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;

/**
 * PUS time used by the simulator is 1 byte for the pfield, 4 bytes seconds and 3 bytes sub-second
 * 
 * The time is started at 0 when the simulator starts and drifts with a constant drift
 *
 */
public class PusTime implements Comparable<PusTime> {
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
        while (fine > 1) {
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

    public static PusTime read(ByteBuffer bb) {
        byte pfield = bb.get();
        if (pfield != TIME_PFIELD) {
            throw new IllegalArgumentException("Expected time pfield " + TIME_PFIELD + ", got " + pfield);
        }
        int seconds = bb.getInt();
        int fractionalTime = ((bb.getShort() & 0xFFFF) << 8) + (bb.get() & 0xFF);
        return new PusTime(seconds, fractionalTime);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (fractionalTime ^ (fractionalTime >>> 32));
        result = prime * result + seconds;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PusTime other = (PusTime) obj;
        if (seconds != other.seconds)
            return false;
        if (fractionalTime != other.fractionalTime)
            return false;
        return true;
    }

    @Override
    public int compareTo(PusTime other) {
        if (this.seconds != other.seconds) {
            return Integer.compare(this.seconds, other.seconds);
        }
        return Long.compare(this.fractionalTime, other.fractionalTime);
    }

    public long deltaMillis(PusTime other) {
        int deltaSeconds = this.seconds - other.seconds;

        long deltaFractionalTime = this.fractionalTime - other.fractionalTime;

        if (deltaFractionalTime < 0) {
            deltaSeconds -= 1;
            deltaFractionalTime += MAX_FRACTIONAL_PART;
        }
        return deltaSeconds * 1000 + deltaFractionalTime * 1000 / MAX_FRACTIONAL_PART;
    }

    public boolean isBefore(PusTime other) {
        return this.compareTo(other) < 0;
    }

    public boolean isAfter(PusTime other) {
        return this.compareTo(other) > 0;
    }

    public PusTime shiftByMillis(int timeShiftMillis) {
        long shiftNanos = timeShiftMillis * 1_000_000L;
        long fractionalShift = (shiftNanos * MAX_FRACTIONAL_PART) / NANOS_IN_SEC;
        long newFractionalTime = this.fractionalTime + fractionalShift;
        int carryOverSeconds = 0;

        if (newFractionalTime > MAX_FRACTIONAL_PART) {
            carryOverSeconds = (int) (newFractionalTime / (MAX_FRACTIONAL_PART + 1));
            newFractionalTime = newFractionalTime % (MAX_FRACTIONAL_PART + 1);
        }

        int newSeconds = this.seconds + timeShiftMillis / 1000 + carryOverSeconds;

        return new PusTime(newSeconds, newFractionalTime);
    }

}
