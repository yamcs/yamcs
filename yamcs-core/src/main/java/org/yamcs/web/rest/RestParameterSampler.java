package org.yamcs.web.rest;

import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-pass sampler for numeric archived parameters, where the recorded data points
 * are not known upfront.
 * <br>
 * The output is not a bunch of parameter values, but instead a range of values limited to n, which
 * should be fit for inclusion in plots.
 * <br>
 * This is *NOT* perfect. The range of returned times is not known upfront, so we take a rough
 * assumption based on the first result, and up until validEnd. Maybe we could use the
 * histogram for this? But afaik that's an optional service.
 */
public class RestParameterSampler {
    
    private static final Logger log = LoggerFactory.getLogger(RestParameterSampler.class);
    private static final int DEFAULT_BUCKET_COUNT = 500;
    
    
    private TreeMap<Long, Sample> samplesByTime;
    private long start;
    private final long projectedEnd;
    private final int bucketCount;
    
    public RestParameterSampler(long projectedEnd) {
        this(projectedEnd, DEFAULT_BUCKET_COUNT);
    }
    
    public RestParameterSampler(long projectedEnd, int bucketCount) {
        this.projectedEnd = projectedEnd;
        this.bucketCount = bucketCount;
        // initializeBuckets();
    }
    
    private void initializeBuckets(long start) {
        this.start = start;
        samplesByTime = new TreeMap<>();
        long step = (projectedEnd - start) / bucketCount;
        for (long i = start; i < projectedEnd; i+=step) {
            samplesByTime.put(i, null);
        }
    }
    
    /**
     * Assumes timesorted processing, as the first entry will be used to
     * determine the time spread of the buckets, up until validEnd. :-/
     */
    public void process(long time, double value) {
        if (time > projectedEnd || time < start) return;
        
        if (samplesByTime == null) {
            initializeBuckets(time);
        }
        
        Entry<Long, Sample> entry = samplesByTime.floorEntry(time);
        if (entry == null) {
            log.warn("No bucket for value " + value);
            return;
        }
        Sample sample = entry.getValue();
        if (sample == null) samplesByTime.put(entry.getKey(), new Sample(time, value));
        else sample.process(time, value);
    }
    
    public List<Sample> collect() {
        if (samplesByTime == null) return Collections.emptyList();
        return samplesByTime.values().stream().filter(s -> s != null).collect(Collectors.toList());
    }

    /**
     * A cumulative sample that keeps track of a rolling average
     * among others.
     */
    public static class Sample {
        long avgt;
        double low;
        double high;
        double avg;
        int n;
        
        public Sample(long t, double value) {
            avgt = t;
            low = avg = high = value;
            n = 1;
        }
        
        public void process(long t, double value) {
            if (value < low) low = value;
            if (value > high) high = value;
            n++;
            avgt -= (avgt / n);
            avgt += (t / n);
            avg -= (avg / n);
            avg += (value / n);
        }
        
        @Override
        public String toString() {
            return String.format("%s (lo=%s, hi=%s, n=%s)", avg, low, high, n);
        }
    }
}
