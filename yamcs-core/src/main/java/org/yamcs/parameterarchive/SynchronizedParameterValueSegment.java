package org.yamcs.parameterarchive;

import java.util.concurrent.locks.ReadWriteLock;

import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.PeekingIterator;

/**
 * Parameter value segment used by the realtime filler which is also used during retrieval.
 * <p>
 * We use a read-write lock in order to avoid retrieval reading inconsistent data
 */
public class SynchronizedParameterValueSegment extends ParameterValueSegment {
    final ReadWriteLock lock;

    public SynchronizedParameterValueSegment(int pid, SortedTimeSegment timeSegment, Type engValueType,
            Type rawValueType, ReadWriteLock lock) {
        super(pid, timeSegment, engValueType, rawValueType);
        this.lock = lock;
    }

    @Override
    public void insertGap(int pos) {
        lock.writeLock().lock();
        try {
            super.insertGap(pos);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void insert(int pos, BasicParameterValue pv) {
        lock.writeLock().lock();
        try {
            super.insert(pos, pv);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public ParameterValueArray getRange(int posStart, int posStop, boolean ascending, boolean retrieveParameterStatus) {
        lock.readLock().lock();
        try {
            return super.getRange(posStart, posStop, ascending, retrieveParameterStatus);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long getSegmentStart() {
        lock.readLock().lock();
        try {
            return super.getSegmentStart();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long getSegmentEnd() {
        lock.readLock().lock();
        try {
            return super.getSegmentEnd();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int numGaps() {
        lock.readLock().lock();
        try {
            return super.numGaps();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int numValues() {
        lock.readLock().lock();
        try {
            return super.numValues();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Value getEngValue(int pos) {
        lock.readLock().lock();
        try {
            return super.getEngValue(pos);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Value getRawValue(int pos) {
        lock.readLock().lock();
        try {
            return super.getRawValue(pos);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public PeekingIterator<TimedValue> newAscendingIterator(long t0) {
        lock.readLock().lock();
        try {
            return new SynchronizedAscendingIterator(t0);
        } finally {
            lock.readLock().unlock();
        }

    }

    @Override
    public PeekingIterator<TimedValue> newDescendingIterator(long t0) {
        lock.readLock().lock();
        try {
            return new SynchronizedDescendingIterator(t0);
        } finally {
            lock.readLock().unlock();
        }
    }

    class SynchronizedAscendingIterator extends AscendingIterator {
        public SynchronizedAscendingIterator(long t0) {
            super(t0);
        }

        @Override
        public void next() {
            lock.readLock().lock();
            try {
                super.next();
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    class SynchronizedDescendingIterator extends DescendingIterator {

        public SynchronizedDescendingIterator(long t0) {
            super(t0);
        }

        @Override
        public void next() {
            lock.readLock().lock();
            try {
                super.next();
            } finally {
                lock.readLock().unlock();
            }
        }
    }
}
