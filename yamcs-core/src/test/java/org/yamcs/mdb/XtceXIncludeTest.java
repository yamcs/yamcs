package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that XTCE files using xi:include load correctly.
 *
 * The test MDB is structured as a modular spacecraft database:
 *
 * <pre>
 *   main.xml  (spacecraft)
 *     xi:include href="tm.xml"   → TM subsystem  (parameters: voltage, packetId)
 *     xi:include href="tc.xml"   → TC subsystem  (command: SWITCH_ON)
 *     xi:include href="tse.xml"  → TSE subsystem (parameter: temperature)
 * </pre>
 */
public class XtceXIncludeTest {

    private static Mdb mdb;

    @BeforeAll
    static void load() throws Exception {
        mdb = MdbFactory.createInstanceByConfig("xtce-xinclude");
    }

    @Test
    public void testRootSpaceSystem() {
        assertNotNull(mdb.getSpaceSystem("/spacecraft"));
    }

    @Test
    public void testTelemetrySubsystem() {
        assertNotNull(mdb.getSpaceSystem("/spacecraft/TM"));
        assertNotNull(mdb.getParameter("/spacecraft/TM/voltage"));
        assertNotNull(mdb.getParameter("/spacecraft/TM/packetId"));
    }

    @Test
    public void testCommandSubsystem() {
        assertNotNull(mdb.getSpaceSystem("/spacecraft/TC"));
        assertNotNull(mdb.getMetaCommand("/spacecraft/TC/SWITCH_ON"));
    }

    @Test
    public void testTseSubsystem() {
        assertNotNull(mdb.getSpaceSystem("/spacecraft/TSE"));
        assertNotNull(mdb.getParameter("/spacecraft/TSE/temperature"));
    }
}
