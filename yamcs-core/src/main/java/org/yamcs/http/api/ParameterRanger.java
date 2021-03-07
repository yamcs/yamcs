package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.Value;
import org.yamcs.parameter.ValueArray;
import org.yamcs.parameterarchive.ParameterValueArray;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.MutableLong;

/**
 * builds ranges of parameters
 * 
 * @author nm
 *
 */
public class ParameterRanger implements Consumer<ParameterValueArray> {
    private static final Logger log = LoggerFactory.getLogger(ParameterRanger.class);
    static final int MAX_RANGES = 500;
    static final int MAX_VALUES = 100;

    // Time in milliseconds. Any gap smaller than this will be ignored. However
    // if the parameter changes value, the ranges will still be split.
    final long minGap;

    // Time in milliseconds. If the distance between two subsequent values of the parameter is bigger than this value
    // (but smaller than the parameter expiration), then an artificial gap will be constructed. This also applies if
    // there is no parameter expiration defined for the parameter.
    final long maxGap;

    // Time in milliseconds of the minimum range to be returned. If the data changes more often, a new range will not be
    // created but the data will be added to the old range.
    final long minRange;

    // max number of values sent
    final int maxValues;

    List<Range> ranges = new ArrayList<>();

    Range curRange = null;
    Value prevValue;
    ParameterStatus prevStatus;
    long prevTimestamp;

    boolean accumulatingDistinct = true;
    Map<Value, MutableLong> distinctValues = new HashMap<>();

    public ParameterRanger(long minGap, long maxGap, long minRange, int maxValues) {
        this.minGap = minGap;
        this.maxGap = maxGap;
        this.minRange = minRange;
        if (maxValues <= 0) {
            maxValues = MAX_VALUES;
        } else if (maxValues > MAX_VALUES) {
            log.warn("Maximum values {} greater than maximum allowed {}, using max ", maxValues, MAX_VALUES);
            maxValues = MAX_VALUES;
        }
        this.maxValues = maxValues;
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

            // if distinct is true, it means the value is part of the distinctValues map
            // if it's false we do not need to keep track of it but we do if it is part of a new range
            boolean distinct = addToDistinct(v);

            ParameterStatus status = statuses[i];
            long timestamp = timestamps[i];

            if (curRange == null) {
                curRange = new SingleRange(timestamp, v);
            } else {
                long stop = checkDataInterruption(prevTimestamp, timestamp, prevStatus);
                if (stop != Long.MIN_VALUE) {// data interruption
                    curRange.stop = stop;
                    potentiallyCreateNewRange(timestamp, v, distinct);
                } else if (!v.equals(prevValue)) {
                    curRange.stop = timestamp;
                    potentiallyCreateNewRange(timestamp, v, distinct);
                } else {
                    curRange.add(v, distinct);
                    curRange.stop = timestamp;
                }
            }
            prevValue = v;
            prevTimestamp = timestamp;
            prevStatus = status;
        }
    }

    // add the value to the distinct values and return true if it has been added or false if there are already more than
    // 2*maxValues distinct values
    private boolean addToDistinct(Value v) {
        MutableLong ml = distinctValues.get(v);
        if (ml != null) {
            ml.increment();
            return true;
        } else if (distinctValues.size() < 2 * maxValues) {
            distinctValues.put(v, new MutableLong(1));
            return true;
        }
        return false;
    }

    // create a new range unless the minRange parameter is in effect and the current range is too small, case in which
    // create a multi value range and add to it
    void potentiallyCreateNewRange(long timestamp, Value v, boolean distinct) {
        if (timestamp - curRange.start < minRange) {
            if (curRange instanceof SingleRange) {
                curRange = new MultiRange((SingleRange) curRange);
            }
            curRange.add(v, distinct);
            curRange.stop = timestamp;
        } else {
            ranges.add(curRange);
            curRange = new SingleRange(timestamp, v);
            if (!distinct) {
                // if a new value appears after a while,
                // we give it a chance to appear among the values part of the final result
                distinctValues.put(v, new MutableLong(1));
            }
        }
    }

    // check for data interruption and return Long.MIN_VALUE if not
    // or the timestamp when the value was last valid if yes
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
        int count;

        public Range(long start, long stop) {
            this.start = start;
            this.stop = stop;
        }

        public abstract void add(Value v, boolean distinct);

        public abstract int valueCount();

        public abstract Value getValue(int idx);

        public abstract int getCount(int idx);

        public int totalCount() {
            return count;
        }
    }

    public static class SingleRange extends Range {

        Value value;

        SingleRange(long start, Value v) {
            super(start, start);
            this.value = v;
            this.count = 1;
        }

        public void add(Value v, boolean distinct) {
            count++;
        }

        public int valueCount() {
            return value == null ? 0 : 1;
        }

        @Override
        public Value getValue(int idx) {
            return value;
        }

        @Override
        public int getCount(int idx) {
            return count;
        }

        @Override
        public String toString() {
            return "SingleRange [value=" + value + ", count=" + count + "]";
        }

    }

    public static class MultiRange extends Range {

        List<Value> values = new ArrayList<>();
        IntArray counts = new IntArray();

        public MultiRange(SingleRange range) {
            super(range.start, range.stop);
            count = range.count;
            counts.add(range.count);
            values.add(range.value);
        }

        // add value to the range;
        // if distinct is false, only increase the total count, not add the value itself
        @Override
        public void add(Value v, boolean distinct) {
            count++;

            int idx = values.indexOf(v);
            if (idx < 0) {
                if (distinct) {
                    values.add(v);
                    counts.add(1);
                }
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

        @Override
        public String toString() {
            return "MultiRange [values=" + values + ", counts=" + counts + "]";
        }
    }

    public List<Range> getRanges() {
        if (curRange != null) {
            ranges.add(curRange);
            curRange = null;
        }
        boolean trimmed = false;
        while (distinctValues.size() > maxValues) {
            // trim down the number of values
            Value min = distinctValues.entrySet().stream()
                    .min((me1, me2) -> Long.compare(me1.getValue().getLong(), me2.getValue().getLong()))
                    .get().getKey();
            distinctValues.remove(min);
            trimmed = true;
        }
        if (trimmed) {
            // remove values which are not in distinct
            for (Range r : ranges) {
                if (r instanceof SingleRange) {
                    SingleRange sr = (SingleRange) r;
                    if (!distinctValues.containsKey(sr.value)) {
                        sr.value = null;
                    }
                } else {
                    MultiRange mr = (MultiRange) r;
                    List<Value> vlist = mr.values;
                    for (int i = vlist.size() - 1; i >= 0; i--) {
                        Value v = vlist.get(i);
                        if (!distinctValues.containsKey(v)) {
                            vlist.remove(i);
                            mr.counts.remove(i);
                        }
                    }
                }
            }
        }
        return ranges;
    }
}
