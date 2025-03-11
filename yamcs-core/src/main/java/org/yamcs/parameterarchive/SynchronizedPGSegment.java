package org.yamcs.parameterarchive;

import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.IntArray;

/**
 * This is a superclass of {@link PGSegment} that provides synchronized access between get and add operations.
 * <p>
 * It is used by the Realtime Parameter filler in order to avoid concurrency issues when reading and writing to the same
 * segment
 */
public class SynchronizedPGSegment extends PGSegment {
    final ReadWriteLock lock = new ReentrantReadWriteLock();

    public SynchronizedPGSegment(int parameterGroupId, long interval, int size) {
        super(parameterGroupId, interval, size);
    }

    @Override
    public void addRecord(long instant, IntArray pids, List<BasicParameterValue> values) {
        lock.writeLock().lock();
        try {
            super.addRecord(instant, pids, values);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    ParameterValueSegment newPvs(int pid, SortedTimeSegment timeSegment, Type engValueType,
            Type rawValueType) {
        return new SynchronizedParameterValueSegment(pid, timeSegment, engValueType, rawValueType, lock);
    }
}
