package org.yamcs.simulator.pus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.simulator.pus.Pus11ServiceTest.TestSim;

public class Pus19ServiceTest {
    static final int TM_TYPE_ACK = 1;
    static final int NACK_START = 4;

    TestSim sim;
    Pus19Service svc;

    @BeforeAll
    static void setupTime() {
        org.yamcs.utils.TimeEncoding.setUp();
    }

    @BeforeEach
    void setup() throws Exception {
        File dir = Files.createTempDirectory("pus19test").toFile();
        dir.deleteOnExit();
        sim = new TestSim(dir);
        svc = new Pus19Service(sim);
    }

    @AfterEach
    void teardown() {
        sim.executor.shutdownNow();
    }

    @Test
    void actionExecutedOnlyWhenEnabled() {
        svc.executeTc(addTc(1));

        // new definitions are disabled
        svc.eventReported(1);
        assertTrue(sim.released.isEmpty());

        svc.executeTc(simpleTc(4, new byte[] { 1, 1 })); // enable definition for event 1
        svc.eventReported(2);
        assertTrue(sim.released.isEmpty());
        svc.eventReported(1);
        svc.eventReported(1);
        assertEquals(2, sim.released.size());
        assertEquals(17, sim.released.get(0).getType()); // the embedded TC[17,1]

        svc.executeTc(simpleTc(9, new byte[0])); // disable the function
        svc.eventReported(1);
        assertEquals(2, sim.released.size());
    }

    @Test
    void duplicateDefinitionRejected() {
        svc.executeTc(addTc(1));
        sim.tm.clear();
        svc.executeTc(addTc(1));
        assertEquals(AbstractPusService.START_ERR_EVENT_ACTION_EXISTS, nackCode());
    }

    @Test
    void deleteEnabledDefinitionRejected() {
        svc.executeTc(addTc(1));
        svc.executeTc(simpleTc(4, new byte[] { 0 })); // enable all
        sim.tm.clear();
        svc.executeTc(simpleTc(2, new byte[] { 1, 1 }));
        assertEquals(AbstractPusService.START_ERR_EVENT_ACTION_ENABLED, nackCode());

        svc.executeTc(simpleTc(5, new byte[] { 1, 1 })); // disable
        sim.tm.clear();
        svc.executeTc(simpleTc(2, new byte[] { 1, 1 }));
        assertEquals(0, svc.definitions.size());
    }

    @Test
    void statusReport() {
        svc.executeTc(addTc(1));
        svc.executeTc(addTc(2));
        svc.executeTc(simpleTc(4, new byte[] { 1, 2 }));
        sim.tm.clear();
        svc.executeTc(simpleTc(6, new byte[0]));

        byte[] report = null;
        for (byte[] p : sim.tm) {
            if (u8(p, 7) == 19 && u8(p, 8) == 7) {
                report = p;
            }
        }
        assertNotNull(report);
        int off = 21; // PusTmPacket.DATA_OFFSET
        assertEquals(2, u8(report, off));
        assertEquals(1, u8(report, off + 1));
        assertEquals(0, u8(report, off + 2)); // event 1 disabled
        assertEquals(2, u8(report, off + 3));
        assertEquals(1, u8(report, off + 4)); // event 2 enabled
    }

    private PusTcPacket simpleTc(int subtype, byte[] userData) {
        PusTcPacket p = new PusTcPacket(1, userData.length, 0, 19, subtype);
        p.getUserDataBuffer().put(userData);
        return p;
    }

    private PusTcPacket addTc(int eventId) {
        byte[] embedded = new PusTcPacket(1, 1, 0, 17, 1).getBytes();
        ByteBuffer ud = ByteBuffer.allocate(2 + embedded.length);
        ud.put((byte) 1); // N
        ud.put((byte) eventId);
        ud.put(embedded);
        return simpleTc(1, ud.array());
    }

    private int nackCode() {
        byte[] found = null;
        for (byte[] p : sim.tm) {
            if (u8(p, 7) == TM_TYPE_ACK && u8(p, 8) == NACK_START) {
                found = p;
            }
        }
        assertNotNull(found);
        return org.yamcs.utils.ByteArrayUtils.decodeInt(found, 21 + 4);
    }

    private static int u8(byte[] b, int i) {
        return b[i] & 0xff;
    }
}
