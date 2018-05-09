package org.yamcs.web.rest.archive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ValueArray;
import org.yamcs.parameterarchive.ParameterValueArray;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.UnsignedLong;

/**
 * One-pass downsampler for time-series data (i.e. numeric archived parameters), where the number of recorded data
 * points are not known upfront.
 * <p>
 * The output is not a bunch of parameter values, but instead a range of values limited to n, which should be fit for
 * inclusion in plots.
 */
public class RestDownsampler implements Consumer<ParameterValueArray> {

    private static final Logger log = LoggerFactory.getLogger(RestDownsampler.class);
    private static final int DEFAULT_SAMPLE_COUNT = 500;
    private static long GAP_TIME = 120000;

    private TreeMap<Long, Sample> samplesByTime = new TreeMap<>();
    private long start;
    private long stop;
    private long lastSampleTime;

    public RestDownsampler(long start, long stop) {
        this(start, stop, DEFAULT_SAMPLE_COUNT);
    }

    public RestDownsampler(long start, long stop, int sampleCount) {
        this.start = start;
        this.stop = stop;

        // Initialize intervals
        long step = (stop - start) / sampleCount;
        for (long i = start; i < stop; i += step) {
            samplesByTime.put(i, null);
        }
    }

    public void process(long time, double value) {
        if (time > stop || time < start) {
            return;
        }

        Entry<Long, Sample> entry = samplesByTime.floorEntry(time);
        if (entry == null) {
            log.warn("No interval for value {}", value);
            return;
        }

        lastSampleTime = entry.getKey();
        Sample sample = entry.getValue();
        if (sample == null) {
            samplesByTime.put(entry.getKey(), new Sample(entry.getKey(), value));
        } else {
            sample.process(value);
        }
    }

    public List<Sample> collect() {
        if (samplesByTime == null) {
            return Collections.emptyList();
        }
        List<Sample> r = new ArrayList<>(DEFAULT_SAMPLE_COUNT);
        Sample prev = null;
        for (Entry<Long, Sample> e : samplesByTime.entrySet()) {
            Sample s = e.getValue();
            if (s == null) {
                long t = e.getKey();
                if ((prev != null) && (t - prev.t > GAP_TIME)) { // generate a gap
                    r.add(new Sample(t));
                }
            } else {
                r.add(s);
                prev = s;
            }
        }

        return r;
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
            process(pval.getGenerationTime(), pval.getEngValue().getUint32Value() & 0xFFFFFFFFL);
            break;
        case UINT64:
            process(pval.getGenerationTime(), pval.getEngValue().getUint64Value());
            break;
        default:
            log.warn("Unexpected value type {}", pval.getEngValue().getType());
        }

    }

    public long lastSampleTime() {
        return lastSampleTime;
    }

    @Override
    public void accept(ParameterValueArray t) {

        ValueArray va = t.getEngValues();
        long[] timestamps = t.getTimestamps();
        int n = timestamps.length;
        Type engType = t.getEngType();

        switch (engType) {
        case FLOAT:
            float[] fv = va.getFloatArray();
            for (int i = 0; i < n; i++) {
                process(timestamps[i], fv[i]);
            }
            break;
        case DOUBLE:
            double[] dv = va.getDoubleArray();
            for (int i = 0; i < n; i++) {
                process(timestamps[i], dv[i]);
            }
            break;
        case UINT32:
            int[] iv = va.getIntArray();
            for (int i = 0; i < n; i++) {
                process(timestamps[i], iv[i] & 0xFFFFFFFFL);
            }
            break;
        case SINT32:
            iv = va.getIntArray();
            for (int i = 0; i < n; i++) {
                process(timestamps[i], iv[i]);
            }
            break;
        case UINT64:
            long[] lv = va.getLongArray();
            for (int i = 0; i < n; i++) {
                process(timestamps[i], UnsignedLong.toDouble(lv[i]));
            }
            break;
        case SINT64:
            lv = va.getLongArray();
            for (int i = 0; i < n; i++) {
                process(timestamps[i], lv[i]);
            }
            break;
        default:
            log.debug("Ignoring value type {}", engType);
        }
    }

    /**
     * A cumulative sample that keeps track of a rolling average among others.
     */
    public static class Sample {
        long t;
        double min;
        double max;
        double avg;
        int n;

        // construct a gap
        Sample(long t) {
            this.t = t;
            n = 0;
            min = avg = max = Double.NaN;
        }

        // sample with one value
        public Sample(long t, double value) {
            this.t = t;
            min = avg = max = value;
            n = 1;
        }

        public void process(double value) {
            if (value < min) {
                min = value;
            }
            if (value > max) {
                max = value;
            }
            n++;
            avg -= (avg / n);
            avg += (value / n);
        }

        @Override
        public String toString() {
            return String.format("%s (min=%s, max=%s, n=%s)", avg, min, max, n);
        }
    }
}
