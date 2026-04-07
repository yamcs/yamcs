package org.yamcs.simulator.pus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class MdbLoaderTest {

    @Test
    void loadsEventDefinitionsFromSimulatorMdb() {
        Map<Integer, String> events = MdbLoader.loadEventDefinitions("/jtyu_mdb.xml");

        assertEquals(Map.of(2, "MagStatus", 1, "IsAttitudeNominal"), events);
    }

    @Test
    void loadsEventReportSubtypesFromService5Containers() {
        List<Integer> subtypes = MdbLoader.loadEventReportSubtypes("/jtyu_mdb.xml");

        assertIterableEquals(List.of(1, 2, 3, 4), subtypes);
    }
}
