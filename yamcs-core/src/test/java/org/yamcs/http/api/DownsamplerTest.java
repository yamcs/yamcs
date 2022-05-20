package org.yamcs.http.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.http.api.Downsampler.Sample;

public class DownsamplerTest {

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
