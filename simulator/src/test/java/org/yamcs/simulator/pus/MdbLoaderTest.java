package org.yamcs.simulator.pus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void derivesHousekeepingSizesForEpsReports() {
        int s9 = MdbLoader.loadHousekeepingPacketSize("/jtyu_mdb.xml", "EPS", 9);
        int s10 = MdbLoader.loadHousekeepingPacketSize("/jtyu_mdb.xml", "EPS", 10);
        int s11 = MdbLoader.loadHousekeepingPacketSize("/jtyu_mdb.xml", "EPS", 11);
        int s12 = MdbLoader.loadHousekeepingPacketSize("/jtyu_mdb.xml", "EPS", 12);
        assertTrue(s9 > 0, "EPS/9 size is " + s9);
        // EPS/10 includes SatBus_Batt_Rd_Cur at bit 320 and it is a 32-bit float, so minimum packet size is 352 bits.
        // This catches accidental matching of a shorter Service 3/26 diagnostic container.
        assertTrue(s10 >= 352, "EPS/10 size is " + s10);
        assertTrue(s10 > 0, "EPS/10 size is " + s10);
        assertTrue(s11 > 0, "EPS/11 size is " + s11);
        assertTrue(s12 > 0, "EPS/12 size is " + s12);
    }
}
