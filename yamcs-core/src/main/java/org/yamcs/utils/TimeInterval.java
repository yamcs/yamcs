package org.yamcs.utils;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Time interval where both ends can be open.
 */
public class TimeInterval {
    private long start;
    private long end;
    private boolean hasStart = false;
    private boolean hasEnd = false;

    public TimeInterval(long start, long end) {
        setStart(start);
        setEnd(end);
    }

    /**
     * Creates a TimeInterval with no start and no end
     */
    public TimeInterval() {
    }

    public TimeInterval(TimeInterval other) {
        this.start = other.start;
        this.end = other.end;
        this.hasStart = other.hasStart;
        this.hasEnd = other.hasEnd;
    }

    /**
     * creates a TimeInterval with no start but with an end
     */
    public static TimeInterval openStart(long end) {
        TimeInterval ti = new TimeInterval();
        ti.setEnd(end);
        return ti;
    }

    public static TimeInterval openEnd(long start) {
        TimeInterval ti = new TimeInterval();
        ti.setStart(start);
        return ti;
    }

    public boolean hasStart() {
        return hasStart;
    }

    public boolean hasEnd() {
        return hasEnd;
    }

    public void setStart(long start) {
        hasStart = true;
        this.start = start;
    }

    public long getStart() {
        return start;
    }

    public void setEnd(long end) {
        hasEnd = true;
        this.end = end;
    }

    public long getEnd() {
        return end;
    }

    /**
     * Checks that [this.start, this.end) contains t
     */
    public boolean contains0(long t) {
        return !((hasStart && t < start) || (hasEnd && t >= end));
    }

    /**
     * Checks that [this.start, this.end] overlaps with [t1.start, t1.end)
     * 
     */
    boolean overlaps1(TimeInterval t1) {
        return !((t1.hasStart && hasEnd && t1.start > end) ||
                (t1.hasEnd && hasStart && start >= t1.end));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        if (hasStart) {
            sb.append(start);
        }
        sb.append(",");
        if (hasEnd) {
            sb.append(end);
        }
        sb.append(")");
        return sb.toString();
    }

    public String toStringEncoded() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        if (hasStart) {
            sb.append(TimeEncoding.toString(start));
        }
        sb.append(",");
        if (hasEnd) {
            sb.append(TimeEncoding.toString(end));
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Filters an input iterator to the intervals that match the given timeInterval
     *
     */
    public static class FilterOverlappingIterator<T extends TimeInterval> implements Iterator<T> {
        TimeInterval timeInterval;
        T next;
        Iterator<T> it;

        /**
         * Creates a new Interator that iterates the elements of inputIterator and outputs only those that overalp with
         * timeInterval.
         * 
         * The timeInterval is considered closed at both ends [start, end] whereas the elements of the inputIterator are
         * considered closed at start but open at end [start, end)
         * 
         * The inputIterator is assumed to contain elements sorted by the start.
         * 
         */
        public FilterOverlappingIterator(TimeInterval timeInterval, Iterator<T> inputIterator) {
            this.timeInterval = timeInterval;
            this.it = inputIterator;
            while (it.hasNext()) {
                T n = it.next();
                if (timeInterval.overlaps1(n)) {
                    next = n;
                    break;
                }
            }
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public T next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            T r = next;
            getNext();
            return r;
        }

        private void getNext() {
            if (it.hasNext()) {
                next = it.next();
                if (timeInterval.hasEnd() && next.hasStart() && timeInterval.getEnd() < next.getStart()) {
                    next = null;
                }
            } else {
                next = null;
            }
        }
    }

}
