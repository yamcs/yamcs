package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.Value;
import org.yamcs.parameter.ValueArray;
import org.yamcs.parameterarchive.ParameterValueArray;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.IntArray;

/**
 * builds ranges of parameters
 * 
 * @author nm
 *
 */
public class ParameterRanger implements Consumer<ParameterValueArray> {
    private static final Logger log = LoggerFactory.getLogger(ParameterRanger.class);
    static final int MAX_RANGES = 500;

    // any gap (detected based on parameter expiration) smaller than this will be ignored
    final long minGap;

    // if distance between parameters (detected based on parameter expiration) is bigger than this, then an artificial
    // gap will be constructed
    final long maxGap;

    // if the parameter value changes more often than this, a multi value range will be constructed
    // if negative, it is not used
    final long minRange;

    List<Range> ranges = new ArrayList<>();

    Range curRange = null;
    Value prevValue;
    ParameterStatus prevStatus;
    long prevTimestamp;

    public ParameterRanger(long minGap, long maxGap, long minRange) {
        this.minGap = minGap;
        this.maxGap = maxGap;
        this.minRange = minRange;
    }

    @Override
    public void accept(ParameterValueArray pva) {
        if (ranges.size() >= MAX_RANGES) {
            log.warn("Maximum number of ranges reached, ignoring further data.", ranges.size());
            return;
        }

        long[] timestamps = pva.getTimestamps();
        ParameterStatus[] statuses = pva.getStatuses();
        ValueArray va = pva.getEngValues();
        int n = va.size();
        Type type = va.getType();

        if (curRange != null && type != curRange.getValue(0).getType()) {
            ranges.add(curRange);
            curRange = null;
        }

        for (int i = 0; i < n; i++) {
            Value v = va.getValue(i);
            ParameterStatus status = statuses[i];
            long timestamp = timestamps[i];

            if (curRange == null) {
                curRange = new SingleRange(timestamp, v);
            } else {
                long stop = checkDataInterruption(prevTimestamp, timestamp, prevStatus);
                if (stop != Long.MIN_VALUE) {
                    curRange.stop = stop;
                    potentiallyCreateNewRange(timestamp, v);
                } else if (!v.equals(prevValue)) {
                    curRange.stop = timestamp;
                    potentiallyCreateNewRange(timestamp, v);
                } else {
                    curRange.add(v);
                    curRange.stop = timestamp;
                }
            }
            prevValue = v;
            prevTimestamp = timestamp;
            prevStatus = status;
        }
    }

    // create a new range unless the minRange parameter is in effect and the current range is too small, case in which
    // create a multi value range and add to it
    void potentiallyCreateNewRange(long timestamp, Value v) {
        if (timestamp - curRange.start < minRange) {
            if (curRange instanceof SingleRange) {
                curRange = new MultiRange((SingleRange) curRange);
            }
            curRange.add(v);
            curRange.stop = timestamp;
        } else {
            ranges.add(curRange);
            curRange = new SingleRange(timestamp, v);
        }
    }

    // check for data interruption and return Long.MIN_VALUE if not or the timestamp when the value was last valid if
    // yes
    private long checkDataInterruption(long prevTimestamp, long timestamp, ParameterStatus prevStatus) {
        long delta = timestamp - prevTimestamp;

        if (delta < minGap) {
            return Long.MIN_VALUE;
        }

        if (prevStatus.hasExpireMillis() && delta > prevStatus.getExpireMillis()) {
            return prevTimestamp + prevStatus.getExpireMillis();
        }

        if (delta > maxGap) {
            return prevTimestamp + maxGap;
        }
        return Long.MIN_VALUE;
    }

    public abstract static class Range {
        long start;
        long stop;

        public Range(long start, long stop) {
            this.start = start;
            this.stop = stop;
        }

        public abstract void add(Value v);

        public abstract int valueCount();

        public abstract Value getValue(int idx);

        public abstract int getCount(int idx);
    }

    public static class SingleRange extends Range {
        Value value;
        int count;

        SingleRange(long start, Value v) {
            super(start, start);
            this.value = v;
            this.count = 1;
        }

        public void add(Value v) {
            count++;
        }

        public int getCount() {
            return count;
        }

        @Override
        public int valueCount() {
            return 1;
        }

        @Override
        public Value getValue(int idx) {
            return value;
        }

        @Override
        public int getCount(int idx) {
            return count;
        }
    }

    public static class MultiRange extends Range {
        List<Value> values = new ArrayList<>();
        IntArray counts = new IntArray();

        public MultiRange(SingleRange range) {
            super(range.start, range.stop);
            counts.add(range.count);
            values.add(range.value);
        }

        @Override
        public void add(Value v) {
            int idx = values.indexOf(v);
            if (idx < 0) {
                values.add(v);
                counts.add(1);
            } else {
                counts.set(idx, counts.get(idx) + 1);
            }
        }

        @Override
        public int valueCount() {
            return counts.size();
        }

        @Override
        public Value getValue(int idx) {
            return values.get(idx);
        }

        @Override
        public int getCount(int idx) {
            return counts.get(idx);
        }
    }

    public List<Range> getRanges() {
        if (curRange != null) {
            ranges.add(curRange);
            curRange = null;
        }
        return ranges;
    }
}
