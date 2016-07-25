package org.yamcs.web.rest.archive;

import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-pass downsampler for time-series data (i.e. numeric archived parameters),
 * where the number of recorded data points are not known upfront.
 * <p>
 * The output is not a bunch of parameter values, but instead a range of values
 * limited to n, which should be fit for inclusion in plots.
 * <p>
 * This is *NOT* perfect. The range of returned items is not known upfront, so
 * a rough assumption is made based on the first result, and up until validEnd.
 */
public class RestDownsampler {

    private static final Logger log = LoggerFactory.getLogger(RestDownsampler.class);
    private static final int DEFAULT_INTERVAL_COUNT = 500;


    private TreeMap<Long, Sample> samplesByTime;
    private long start;
    private final long projectedEnd;
    private final int intervalCount;
    private long lastSampleTime;

    public RestDownsampler(long projectedEnd) {
        this(projectedEnd, DEFAULT_INTERVAL_COUNT);
    }

    public RestDownsampler(long projectedEnd, int intervalCount) {
        this.projectedEnd = projectedEnd;
        this.intervalCount = intervalCount;
    }

    private void initializeIntervals(long start) {
        this.start = start;
        samplesByTime = new TreeMap<>();
        long step = (projectedEnd - start) / intervalCount;
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
        lastSampleTime = time;

        if (samplesByTime == null) {
            initializeIntervals(time);
        }

        Entry<Long, Sample> entry = samplesByTime.floorEntry(time);
        if (entry == null) {
            log.warn("No interval for value " + value);
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
        double min;
        double max;
        double avg;
        int n;

        public Sample(long t, double value) {
            avgt = t;
            min = avg = max = value;
            n = 1;
        }

        public void process(long t, double value) {
            if (value < min) min = value;
            if (value > max) max = value;
            n++;
            avgt -= (avgt / n);
            avgt += (t / n);
            avg -= (avg / n);
            avg += (value / n);
        }

        @Override
        public String toString() {
            return String.format("%s (min=%s, max=%s, n=%s)", avg, min, max, n);
        }
    }

    public void process(org.yamcs.parameter.ParameterValue pval) {
        if (pval.getEngValue() == null) {
            return;
        }

        switch (pval.getEngValue().getType()) {
        case DOUBLE:
            process(pval.getGenerationTime(), pval.getEngValue().getDoubleValue());
            break;
        case FLOAT:
            process(pval.getGenerationTime(), pval.getEngValue().getFloatValue());
            break;
        case SINT32:
            process(pval.getGenerationTime(), pval.getEngValue().getSint32Value());
            break;
        case SINT64:
            process(pval.getGenerationTime(), pval.getEngValue().getSint64Value());
            break;
        case UINT32:
            process(pval.getGenerationTime(), pval.getEngValue().getUint32Value()&0xFFFFFFFFL);
            break;
        case UINT64:
            process(pval.getGenerationTime(), pval.getEngValue().getUint64Value());
            break;
        default:
            log.warn("Unexpected value type " + pval.getEngValue().getType());
        }

    }

    public long lastSampleTime() {
        return lastSampleTime;
    }
}
