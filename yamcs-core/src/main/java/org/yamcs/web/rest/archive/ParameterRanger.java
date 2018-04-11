package org.yamcs.web.rest.archive;

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

    List<Range> ranges = new ArrayList<>();

    Range curRange = null;
    Value prevValue;
    ParameterStatus prevStatus;
    long prevTimestamp;

    public ParameterRanger(long minGap, long maxGap) {
        this.minGap = minGap;
        this.maxGap = maxGap;
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

        if (curRange != null && type != curRange.v.getType()) {
            ranges.add(curRange);
            curRange = null;
        }
        
        
        for (int i = 0; i < n; i++) {
            Value v = va.getValue(i);
            ParameterStatus status = statuses[i];
            long timestamp = timestamps[i];

            if (curRange == null) {
                curRange = new Range(timestamp, v);
            } else {
                long stop  = checkDataInterruption(prevTimestamp, timestamp, prevStatus);
                if(stop != Long.MIN_VALUE) {
                    curRange.stop = stop;
                    
                    ranges.add(curRange);
                    curRange = new Range(timestamp, v);
                } else if (!v.equals(prevValue)) {
                    curRange.stop = timestamp;
                    
                    ranges.add(curRange);
                    curRange = new Range(timestamp, v);
                } else {
                    curRange.count++;
                    curRange.stop = timestamp;
                }
            }
            prevValue = v;
            prevTimestamp = timestamp;
            prevStatus = status;
        }
    }

    //check for data interruption and return Long.MIN_VALUE if not or the timestamp when the value was last valid if yes
    private long checkDataInterruption(long prevTimestamp, long timestamp, ParameterStatus prevStatus) {
        long delta = timestamp - prevTimestamp;

        if (delta < minGap) {
            return Long.MIN_VALUE;
        }

        if (prevStatus.hasExpireMillis() && delta > prevStatus.getExpireMillis()) {
            return prevTimestamp+prevStatus.getExpireMillis();
        }
        
        if(delta > maxGap) {
            return prevTimestamp+maxGap;
        }
        return Long.MIN_VALUE;
    }

    public static class Range {
        Value v;
        long start;
        long stop;
        int count;
        Range(long start, Value v) {
            this.start = start;
            this.stop = start;
            this.v = v;
            this.count = 1;
        }
    }
    
    
    public List<Range> getRanges() {
        if(curRange!=null) {
            ranges.add(curRange);
            curRange = null;
        }
        return ranges;
    }
}
