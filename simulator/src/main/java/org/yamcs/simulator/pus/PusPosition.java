package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;

/**
 * Position tag used by the ST[22] (position-based scheduling) simulator: 4 bytes orbit number, 2 bytes orbit angle.
 * <p>
 * ECSS-E-ST-70-41C 6.22.4 defines a position tag as an orbit number (non-wrapping) and a position within that orbit
 * (an angle, cyclic). The wire format ("mission-specific" per the standard) used here is a raw binary angle measure:
 * {@code angleTicks} in [0, TICKS_PER_ORBIT) represents [0, 360) degrees.
 */
public class PusPosition implements Comparable<PusPosition> {
    public static final int LENGTH_BYTES = 6;
    public static final int TICKS_PER_ORBIT = 1 << 16;

    final long orbitNumber;
    // 0 .. TICKS_PER_ORBIT-1, representing 0..360 degrees
    final int angleTicks;

    public PusPosition(long orbitNumber, int angleTicks) {
        this.orbitNumber = orbitNumber;
        this.angleTicks = angleTicks;
    }

    public void encode(ByteBuffer bb) {
        bb.putInt((int) orbitNumber);
        bb.putShort((short) angleTicks);
    }

    public static PusPosition read(ByteBuffer bb) {
        long orbitNumber = bb.getInt() & 0xFFFFFFFFL;
        int angleTicks = bb.getShort() & 0xFFFF;
        return new PusPosition(orbitNumber, angleTicks);
    }

    @Override
    public String toString() {
        return "PusPosition [orbitNumber=" + orbitNumber + ", angleTicks=" + angleTicks + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + angleTicks;
        result = prime * result + (int) (orbitNumber ^ (orbitNumber >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PusPosition other = (PusPosition) obj;
        return orbitNumber == other.orbitNumber && angleTicks == other.angleTicks;
    }

    @Override
    public int compareTo(PusPosition other) {
        if (this.orbitNumber != other.orbitNumber) {
            return Long.compare(this.orbitNumber, other.orbitNumber);
        }
        return Integer.compare(this.angleTicks, other.angleTicks);
    }

    public boolean isBefore(PusPosition other) {
        return this.compareTo(other) < 0;
    }

    public boolean isAfter(PusPosition other) {
        return this.compareTo(other) > 0;
    }

    /**
     * Signed difference {@code this - other} expressed in angle ticks (i.e. orbit number differences are scaled by
     * {@link #TICKS_PER_ORBIT}). Used to compute how long (in simulated orbit time) until a scheduled position is
     * reached.
     */
    public long deltaTicks(PusPosition other) {
        return (this.orbitNumber - other.orbitNumber) * TICKS_PER_ORBIT + (this.angleTicks - other.angleTicks);
    }

    /**
     * Shift this position by a signed number of angle ticks, carrying over into the orbit number as needed (a
     * negative shift can move to an earlier orbit).
     */
    public PusPosition shiftByTicks(long deltaTicks) {
        long total = angleTicks + deltaTicks;
        long orbitDelta = Math.floorDiv(total, TICKS_PER_ORBIT);
        int newAngleTicks = (int) Math.floorMod(total, TICKS_PER_ORBIT);
        return new PusPosition(orbitNumber + orbitDelta, newAngleTicks);
    }
}
