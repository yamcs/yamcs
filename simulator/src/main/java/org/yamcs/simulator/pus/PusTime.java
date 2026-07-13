package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;

public class PusTime implements Comparable<PusTime> {
    final long millis;

    public PusTime(long millis) {
        this.millis = millis;
    }

    long millis() {
        return millis;
    }

    public void encode(ByteBuffer bb, PusTimeEncoding timeEncoding) {
        timeEncoding.encode(this, bb);
    }

    @Override
    public String toString() {
        return "PusTime [millis=" + millis + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (millis ^ (millis >>> 32));
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
        if (millis != other.millis)
            return false;
        return true;
    }

    @Override
    public int compareTo(PusTime other) {
        return Long.compare(this.millis, other.millis);
    }

    public long deltaMillis(PusTime other) {
        return millis - other.millis;
    }

    public boolean isBefore(PusTime other) {
        return this.compareTo(other) < 0;
    }

    public boolean isAfter(PusTime other) {
        return this.compareTo(other) > 0;
    }

    public PusTime shiftByMillis(int timeShiftMillis) {
        return new PusTime(millis + timeShiftMillis);
    }
}
