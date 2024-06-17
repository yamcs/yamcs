package org.yamcs.utils;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A list of sorted, non overlapping {@link TimeInterval}
 * <p>
 * The intervals are considered closed at start and open at end [start, end)
 * <p>
 * It is implemented as a copy on write array and it is thread safe.
 * 
 * @author nm
 *
 */
public class PartitionedTimeInterval<T extends TimeInterval> implements Iterable<T> {
    private TimeInterval[] intervals;
    final transient ReentrantLock lock = new ReentrantLock();

    public PartitionedTimeInterval() {
        intervals = new TimeInterval[0];
    }

    /**
     * Insert a new time interval in the list, if it doesn't overlap with the
     * existing intervals.
     * 
     * If it overlaps but the overlap is within the tolerance (on either end),
     * it modifies it such as to fit perfectly
     * 
     * If it overlaps and the overlap is not within the tolerance margin, then
     * it doesn't do anything and it returns null;
     * 
     * This operation assumes that the lengths of the interval is at least twice
     * the tolerance.
     * 
     * @return returns the possibly modified inserted interval
     */
    public T insert(T x, long tolerance) {
        lock.lock();
        try {
            TimeInterval[] tmp = intervals;
            if (tmp.length == 0) {
                tmp = new TimeInterval[1];
                tmp[0] = x;
                intervals = tmp;
                return x;
            }
            if (!x.hasStart() && !x.hasEnd()) {
                return null;
            }

            if (!x.hasStart()) {
                return insertFirst(tmp, x, tolerance);
            }

            if (!x.hasEnd()) {
                return insertLast(tmp, x, tolerance);
            }
            // here timeInterval has both start and stop
            // do a binary search for the start

            int low = 0;
            int high = tmp.length - 1;

            while (low <= high) {
                int mid = (low + high) >>> 1;
                TimeInterval ti = tmp[mid];

                if (ti.hasStart() && ti.getStart() + tolerance > x.getStart()) {
                    high = mid - 1;
                } else {
                    low = mid + 1;
                }
            }
            // we have to put it on position low
            if (low == 0) {
                return insertFirst(tmp, x, tolerance);
            } else if (low == tmp.length) {
                return insertLast(tmp, x, tolerance);
            }
            TimeInterval prev = tmp[low - 1];

            if (prev.getEnd() - tolerance > x.getStart()) {
                return null;
            }
            if (prev.getEnd() + tolerance > x.getStart()) {
                x.setStart(prev.getEnd());
            }
            TimeInterval next = tmp[low];
            if (x.getEnd() - tolerance > next.getStart()) {
                return null;
            }
            if (x.getEnd() + tolerance > next.getStart()) {
                x.setEnd(next.getStart());
            }
            TimeInterval[] newIntervals = new TimeInterval[tmp.length + 1];

            System.arraycopy(tmp, 0, newIntervals, 0, low);
            newIntervals[low] = x;
            System.arraycopy(tmp, low, newIntervals, low + 1, tmp.length - low);
            intervals = newIntervals;
            return x;

        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts an interval in the list if it doesn't overlap with an existing one.
     * 
     * @return returns the interval inserted
     */
    public T insert(T timeInterval) {
        return insert(timeInterval, 0);
    }

    private T insertFirst(TimeInterval[] tmp, T x, long tolerance) {
        TimeInterval t0 = tmp[0];
        if (!t0.hasStart()) {
            return null;
        }
        if (x.getEnd() - tolerance <= t0.getStart()) {
            if (x.getEnd() + tolerance > t0.getStart()) {
                x.setEnd(t0.getStart());
            }
            TimeInterval[] newIntervals = new TimeInterval[tmp.length + 1];
            newIntervals[0] = x;
            System.arraycopy(tmp, 0, newIntervals, 1, tmp.length);
            intervals = newIntervals;
            return x;
        } else {
            return null;
        }
    }

    private T insertLast(TimeInterval[] tmp, T x, long tolerance) {
        TimeInterval tn = tmp[tmp.length - 1];
        if (!tn.hasEnd()) {
            return null;
        }
        if (tn.getEnd() - tolerance <= x.getStart()) {
            if (tn.getEnd() + tolerance > x.getStart()) {
                x.setStart(tn.getEnd());
            }
            TimeInterval[] newIntervals = Arrays.copyOf(tmp, tmp.length + 1);
            newIntervals[tmp.length] = x;
            intervals = newIntervals;
            return x;
        } else {
            return null;
        }
    }

    /**
     * Creates an iterator that iterates over all the timeintervals overlapping
     * with timeInterval The timeInterval is considered closed at both ends
     * [start, stop]
     */
    public Iterator<T> overlappingIterator(TimeInterval timeInterval) {
        return new TimeInterval.FilterOverlappingIterator<>(timeInterval, iterator());
    }

    /**
     * Creates an iterator that iterates over all the timeintervals overlapping
     * with timeInterval The timeInterval is considered closed at both ends
     * [start, stop]
     */
    public Iterator<T> overlappingReverseIterator(TimeInterval timeInterval) {
        return new TimeInterval.FilterOverlappingIterator<>(timeInterval, reverseIterator());
    }

    /**
     * returns an interval where t would fit or null if there is no such
     * interval
     * 
     * @return ti such that ti.getStart() &lt;= t &lt; ti.getEnd()
     */
    @SuppressWarnings("unchecked")
    public T getFit(long t) {
        TimeInterval[] tmp = intervals;
        int low = 0;
        int high = tmp.length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            TimeInterval ti = tmp[mid];

            if (ti.hasStart() && ti.getStart() > t) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        if (low == 0) {
            return null;
        }
        TimeInterval ti = tmp[low - 1];
        if (ti.hasEnd() && ti.getEnd() <= t) {
            return null;
        } else {
            return (T) ti;
        }
    }

    public int size() {
        return intervals.length;
    }

    @Override
    public Iterator<T> iterator() {
        return new AscendingIterator<>(intervals);
    }

    public Iterator<T> reverseIterator() {
        return new DescendingIterator<>(intervals);
    }

    public TimeInterval get(int i) {
        return intervals[i];
    }

    public boolean isEmpty() {
        return size() == 0;
    }


    static class AscendingIterator<T> implements Iterator<T> {
        TimeInterval[] snapshot;
        int cur = 0;

        AscendingIterator(TimeInterval[] intervals) {
            this.snapshot = intervals;
        }

        @Override
        public boolean hasNext() {
            return cur < snapshot.length;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return (T) snapshot[cur++];
        }
    }

    static class DescendingIterator<T> implements Iterator<T> {
        TimeInterval[] snapshot;
        int cur;

        DescendingIterator(TimeInterval[] intervals) {
            this.snapshot = intervals;
            this.cur = snapshot.length - 1;
        }

        @Override
        public boolean hasNext() {
            return cur >= 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return (T) snapshot[cur--];
        }
    }


}
