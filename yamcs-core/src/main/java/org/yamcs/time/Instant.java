package org.yamcs.time;

import org.yamcs.utils.TimeEncoding;

/**
 * Yamcs high resolution instant storing milliseconds since 1970-01-01T00:00:00 TAI (including leap seconds) and
 * picoseconds.
 * <p>
 * Most of the Yamcs classes use just the milliseconds.
 * 
 * @author nm
 *
 */
public class Instant implements Comparable<Instant> {
    public static final Instant INVALID_INSTANT = new Instant(TimeEncoding.INVALID_INSTANT);
    static final long PICOS_PER_MILLIS = 1000_000_000;

    public static final long MIN_INSTANT = Long.MIN_VALUE + 1;
    public static final long MAX_INSTANT = 185539080470435999L;

    private final long millis;
    private final int picos;

    private Instant(long millis, int picos) {

        this.millis = millis;
        this.picos = picos;
    }

    private Instant(long millis) {
        this(millis, 0);
    }

    /**
     * Create a new instant given the number of milliseconds and the number of picoseconds
     * 
     * @param millis
     * @param picos
     * @return
     */
    public static Instant get(long millis, long picos) {
        int picos1;

        if (picos >= PICOS_PER_MILLIS || picos < 0) {
            millis = Math.addExact(millis, Math.floorDiv(picos, PICOS_PER_MILLIS));
            picos1 = (int) Math.floorMod(picos, PICOS_PER_MILLIS);
        } else {
            picos1 = (int) picos;
        }
        if (millis > MAX_INSTANT || millis < MIN_INSTANT) {
            throw new IllegalArgumentException("instant exceeds the limit");
        }
        return new Instant(millis, picos1);
    }

    /**
     * Returns a new instant with the given milliseconds and the picos 0
     * 
     * @param millis
     * @return
     */
    public static Instant get(long millis) {
        if(millis == TimeEncoding.INVALID_INSTANT) {
            return INVALID_INSTANT;
        }
        if (millis > MAX_INSTANT || millis < MIN_INSTANT) {
            throw new IllegalArgumentException("instant exceeds the limit");
        }
        return new Instant(millis, 0);
    }

    public long getMillis() {
        return millis;
    }

    public int getPicos() {
        return picos;
    }

    /**
     * Add the given instant to this and return the result.
     * 
     * @param t
     * @return
     */
    public Instant plus(Instant t) {
        if ((t.millis | t.picos) == 0) {
            return this;
        }

        long millis = Math.addExact(this.millis, t.millis);
        int picos = this.picos + t.picos;

        return get(millis, picos);
    }

    /**
     * Add the given number of seconds to this and return the result
     * 
     * @param secs
     * @return
     */
    public Instant plus(double secs) {
        double millis = 1000 * secs;
        if (millis > Long.MAX_VALUE || millis < Long.MIN_VALUE) {
            throw new IllegalArgumentException("seconds value exceeds the limit");
        }

        long m = (long) millis;
        int p = (int) Math.round((millis - m) * PICOS_PER_MILLIS);
        return get(Math.addExact(this.millis, m), this.picos + p);
    }

    /**
     * Compute the distance in seconds between this instant and the given instant.
     * 
     * @param t
     * @return
     */
    public double deltaFrom(Instant t) {
        double d = 0.001 * (this.millis - t.millis);
        d += (this.picos - t.picos) * 1e-12;

        return d;
    }

    @Override
    public int compareTo(Instant o) {
        int cmp = Long.compare(millis, o.millis);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(picos, o.picos);
    }

    @Override
    public int hashCode() {
        return ((int) (millis ^ (millis >>> 32))) + 51 * picos;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        Instant other = (Instant) obj;
        if (millis != other.millis)
            return false;
        if (picos != other.picos)
            return false;
        return true;
    }

    @Override
    public String toString() {
        if (TimeEncoding.isSetUp()) {
            return TimeEncoding.toString(this.millis);
        } else {
            return "Instant [millis=" + millis + ", picos=" + picos + "]";
        }
    }

}
