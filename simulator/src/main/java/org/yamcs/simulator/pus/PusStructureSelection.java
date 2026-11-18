package org.yamcs.simulator.pus;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Per-process structure selection. An empty rate map selects every structure at native rate. */
final class PusStructureSelection {
    final Map<Integer, Integer> rates = new LinkedHashMap<>();
    private final Map<Integer, Integer> samples = new HashMap<>();

    void setRate(int id, int rate) {
        rates.put(id, rate);
        samples.remove(id);
    }

    boolean accepts(int id) {
        if (rates.isEmpty()) {
            return true;
        }
        Integer rate = rates.get(id);
        if (rate == null) {
            return false;
        }
        // Mission convention: 0 and 1 keep every packet; N>1 keeps the first and every Nth thereafter.
        int period = Math.max(1, rate);
        int sample = samples.getOrDefault(id, 0);
        samples.put(id, (sample + 1) % period);
        return sample == 0;
    }
}
