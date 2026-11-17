package org.yamcs.http.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yamcs.http.api.Downsampler.Sample;
import org.yamcs.parameter.EnumeratedValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ValueArray;
import org.yamcs.parameterarchive.ParameterValueArray;
import org.yamcs.parameterarchive.ParameterValueSegment;
import org.yamcs.parameterarchive.SortedTimeSegment;
import org.yamcs.protobuf.Yamcs.Value.Type;

public class DownsamplerTest {

    @Test
    public void testArchivedEnumSamplingMatchesLiveValues() {
        var timestamps = new SortedTimeSegment(0);
        var segment = new ParameterValueSegment(0, timestamps, Type.ENUMERATED, null);
        var liveSampler = new Downsampler(0, 20, 2);
        liveSampler.enableCategoricalMode(Map.of("OFF", 0, "ON", 1));

        long[] times = { 1, 2, 11 };
        long[] ordinals = { 0, 1, 0 };
        String[] labels = { "OFF", "ON", "OFF" };
        for (int i = 0; i < times.length; i++) {
            var pv = new ParameterValue("state");
            pv.setGenerationTime(times[i]);
            pv.setEngValue(new EnumeratedValue(ordinals[i], labels[i]));
            segment.insert(i, pv);
            timestamps.add(times[i]);
            liveSampler.process(pv);
        }

        var archivedSampler = new Downsampler(0, 20, 2);
        archivedSampler.enableCategoricalMode(Map.of("OFF", 0, "ON", 1));
        archivedSampler.accept(segment.getRange(0, 3, true, false));

        for (var sampler : List.of(liveSampler, archivedSampler)) {
            var samples = sampler.collect();
            assertEquals(2, samples.size());
            assertEquals(1, samples.get(0).avg);
            assertEquals(1, samples.get(0).min);
            assertEquals(1, samples.get(0).max);
            assertEquals(2, samples.get(0).n);
            assertEquals(0, samples.get(1).avg);
        }
    }

    @Test
    public void testArchivedUnknownEnumLabelProducesGap() {
        var sampler = new Downsampler(0, 20, 2);
        sampler.enableCategoricalMode(Map.of("ON", 1));
        sampler.accept(new ParameterValueArray(new long[] { 1, 11 },
                new ValueArray(new String[] { "ON", "UNDEFINED" }), null, null));

        var samples = sampler.collect();
        assertEquals(2, samples.size());
        assertEquals(1, samples.get(0).avg);
        assertTrue(Double.isNaN(samples.get(1).avg));
    }

    @Test
    public void testStringWithoutEnumMappingProducesGap() {
        var sampler = new Downsampler(0, 10, 1);
        sampler.accept(new ParameterValueArray(new long[] { 1 },
                new ValueArray(new String[] { "ON" }), null, null));

        var samples = sampler.collect();
        assertEquals(1, samples.size());
        assertTrue(Double.isNaN(samples.get(0).avg));
    }

    @Test
    public void testSampling() {
        Downsampler sampler = new Downsampler(1, 10, 3);

        List<Sample> samples = sampler.collect();
        assertEquals(0, samples.size());

        sampler.process(1, 5, -1);

        samples = sampler.collect();
        assertEquals(1, samples.size());
        assertEquals(5, samples.get(0).avg, 1e-10);
        assertEquals(1, samples.get(0).n);

        // Add to same bucket
        sampler.process(2, 10, -1);
        assertEquals(1, samples.size());
        assertEquals((5 + 10) / 2., samples.get(0).avg, 1e-10);
        assertEquals(2, samples.get(0).n);

        // Add to same bucket
        sampler.process(3, 7, -1);
        samples = sampler.collect();
        assertEquals(1, samples.size());
        assertEquals((5 + 10 + 7) / 3., samples.get(0).avg, 1e-10);
        assertEquals(3, samples.get(0).n);

        // Due to flooring, leads to a new bucket
        sampler.process(4, 2, -1);
        samples = sampler.collect();
        assertEquals(2, samples.size());

        Sample sample0 = samples.get(0);
        assertEquals((5 + 10 + 7) / 3., sample0.avg, 1e-10);
        assertEquals(5, sample0.min, 1e-10);
        assertEquals(10, sample0.max, 1e-10);

        Sample sample1 = samples.get(1);
        assertEquals(2, sample1.avg, 1e-10);
        assertEquals(2, sample1.min, 1e-10);
        assertEquals(2, sample1.max, 1e-10);
    }

    @Test
    public void testSamplingTooMany() {
        Downsampler sampler = new Downsampler(1, 2, 3);
        sampler.process(1, 1, -1);
        sampler.process(2, 2, -1);
        sampler.process(2, 2.3, -1);
        List<Sample> samples = sampler.collect();
        assertEquals(1, samples.size());
    }

    @Test
    public void testSamplingInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Downsampler(2, 1, 3);
        });
    }
}
