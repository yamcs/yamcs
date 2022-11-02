package org.yamcs.yarch;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.yamcs.yarch.PartitionManager.Interval;

/**
 * Iterates over time based intervals of a partition
 * 
 * @author nm
 *
 */
public class PartitionIntervalIterator implements Iterator<Interval> {
    final PartitioningSpec partitioningSpec;
    final Iterator<Interval> it;
    final Set<Object> partitionValueFilter;
    Interval next;
    boolean reverse;
    long start;
    boolean jumpToStart = false;

    PartitionIntervalIterator(PartitioningSpec partitioningSpec, Iterator<Interval> it, Set<Object> partitionFilter,
            boolean reverse) {
        this.partitioningSpec = partitioningSpec;
        this.it = it;
        this.partitionValueFilter = partitionFilter;
        this.reverse = reverse;
    }

    public void jumpToStart(long startInstant) {
        this.start = startInstant;
        jumpToStart = true;
    }

    @Override
    public boolean hasNext() {
        if (next != null) {
            return true;
        }

        while (it.hasNext()) {
            Interval intv = it.next();
            if ((!reverse && jumpToStart && intv.hasEnd() && intv.getEnd() <= start) ||
                    (reverse && jumpToStart && intv.hasStart() && intv.getStart() >= start)) {
                continue;
            } else {
                jumpToStart = false;
            }

            next = new Interval(intv);
            for (Map.Entry<Object, Partition> me : intv.partitions.entrySet()) {
                if ((partitionValueFilter == null) || (partitionValueFilter.contains(me.getKey()))) {
                    next.add(me.getKey(), me.getValue());
                }
            }

            if (next.size() > 0) {
                break;
            }
        }
        if (next == null || next.size() == 0) {
            next = null;
            return false;
        } else {
            return true;
        }
    }

    @Override
    public Interval next() {
        Interval ret = next;
        next = null;

        return ret;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("cannot remove partitions like this");
    }
}
