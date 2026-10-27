package org.yamcs.simulator.pus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.simulator.pus.Pus11ServiceTest.TestSim;

/**
 * ST[19] behaviour against ECSS-E-ST-70-41C §6.19.8 (add/delete) and the event-action release path.
 */
public class Pus19ServiceTest {
    static final int TM_TYPE_ACK = 1;
    static final int ACK_START = 3;
    static final int NACK_START = 4;
    static final int ACK_COMPLETION = 7;
    static final int APID = PusSimulator.MAIN_APID;

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
        svc.executeTc(addTc(1, request(17, 1)));
        svc.executeTc(simpleTc(8, new byte[0])); // enable the function

        // new definitions are disabled (§6.19.8.1h.1)
        svc.onEvent(APID, 1);
        assertTrue(sim.released.isEmpty());

        svc.executeTc(simpleTc(4, ids(1))); // enable definition (1,1)
        svc.onEvent(APID, 2);
        assertTrue(sim.released.isEmpty());
        svc.onEvent(APID, 1);
        svc.onEvent(APID, 1);
        assertEquals(2, sim.released.size());
        assertEquals(17, sim.released.get(0).getType()); // the embedded TC[17,1]

        svc.executeTc(simpleTc(9, new byte[0])); // disable the function
        svc.onEvent(APID, 1);
        assertEquals(2, sim.released.size());
    }

    @Test
    void addExistingDisabledDefinitionReplacesRequest() {
        svc.executeTc(addTc(1, request(17, 1)));
        sim.tm.clear();
        byte[] replacement = request(3, 5);
        svc.executeTc(addTc(1, replacement));

        assertTrue(nackStartCodes().isEmpty());
        assertEquals(1, count(ACK_COMPLETION));
        assertArrayEquals(replacement, def(1).request);
    }

    @Test
    void addOverEnabledDefinitionRejected() {
        byte[] original = request(17, 1);
        svc.executeTc(addTc(1, original));
        svc.executeTc(simpleTc(4, ids(1)));
        sim.tm.clear();
        svc.executeTc(addTc(1, request(3, 5)));

        assertEquals(List.of(Pus19Service.START_ERR_EA_DEF_ENABLED), nackStartCodes());
        assertEquals(0, count(ACK_COMPLETION));
        assertArrayEquals(original, def(1).request);
    }

    @Test
    void addProcessesValidInstructionsDespiteFaultyOnes() {
        svc.executeTc(addTc(1, request(17, 1)));
        svc.executeTc(simpleTc(4, ids(1)));
        sim.tm.clear();
        // instruction 1 targets the enabled definition (rejected), instruction 2 is new (valid)
        svc.executeTc(addTc(new int[] { 1, 2 }, request(3, 5), request(3, 6)));

        assertEquals(List.of(Pus19Service.START_ERR_EA_DEF_ENABLED), nackStartCodes());
        assertEquals(0, count(ACK_START));
        assertEquals(1, count(ACK_COMPLETION));
        assertNotNull(def(2));
        assertFalse(def(2).enabled);
    }

    @Test
    void maxDefinitionsEnforced() {
        for (int i = 0; i < Pus19Service.MAX_DEFINITIONS; i++) {
            svc.executeTc(addTc(i, request(17, 1)));
        }
        sim.tm.clear();
        svc.executeTc(addTc(Pus19Service.MAX_DEFINITIONS, request(17, 1)));
        assertEquals(List.of(Pus19Service.START_ERR_MAX_EA_DEFS_REACHED), nackStartCodes());
        assertEquals(Pus19Service.MAX_DEFINITIONS, svc.definitions.size());

        // replacing an existing definition is still possible at the maximum
        sim.tm.clear();
        svc.executeTc(addTc(0, request(3, 5)));
        assertTrue(nackStartCodes().isEmpty());
    }

    @Test
    void addWithTruncatedRequestRejected() {
        byte[] req = request(17, 1);
        byte[] truncated = new byte[req.length - 1];
        System.arraycopy(req, 0, truncated, 0, truncated.length);
        svc.executeTc(addTc(1, truncated));

        assertEquals(List.of(Pus19Service.START_ERR_INVALID_REQUEST), nackStartCodes());
        assertTrue(svc.definitions.isEmpty());
    }

    @Test
    void deleteRejectsPerInstructionAndDeletesValidOnes() {
        svc.executeTc(addTc(1, request(17, 1)));
        svc.executeTc(addTc(2, request(17, 1)));
        svc.executeTc(simpleTc(4, ids(1))); // enable (1,1)
        sim.tm.clear();
        // (1,1) enabled -> rejected, (1,2) valid, (1,9) unknown -> rejected
        svc.executeTc(simpleTc(2, ids(1, 2, 9)));

        assertEquals(List.of(Pus19Service.START_ERR_EA_DEF_ENABLED, Pus19Service.START_ERR_EA_DEF_NOT_FOUND),
                nackStartCodes());
        assertEquals(1, count(ACK_COMPLETION));
        assertNotNull(def(1));
        assertEquals(null, def(2));
    }

    @Test
    void deleteDisabledDefinition() {
        svc.executeTc(addTc(1, request(17, 1)));
        sim.tm.clear();
        svc.executeTc(simpleTc(2, ids(1)));
        assertEquals(1, count(ACK_START));
        assertEquals(1, count(ACK_COMPLETION));
        assertTrue(svc.definitions.isEmpty());
    }

    @Test
    void enableUnknownDefinitionRejectedOthersEnabled() {
        svc.executeTc(addTc(1, request(17, 1)));
        sim.tm.clear();
        svc.executeTc(simpleTc(4, ids(9, 1)));
        assertEquals(List.of(Pus19Service.START_ERR_EA_DEF_NOT_FOUND), nackStartCodes());
        assertTrue(def(1).enabled);
        assertEquals(1, count(ACK_COMPLETION));
    }

    @Test
    void statusReport() {
        svc.executeTc(addTc(1, request(17, 1)));
        svc.executeTc(addTc(2, request(17, 1)));
        svc.executeTc(simpleTc(4, ids(2)));
        sim.tm.clear();
        svc.executeTc(simpleTc(6, new byte[0]));

        ByteBuffer bb = ByteBuffer.wrap(findTm(19, 7), PusTmPacket.DATA_OFFSET, 11);
        assertEquals(2, bb.get());
        assertEquals(APID, bb.getShort());
        assertEquals(1, bb.getShort());
        assertEquals(0, bb.get()); // (1,1) disabled
        assertEquals(APID, bb.getShort());
        assertEquals(2, bb.getShort());
        assertEquals(1, bb.get()); // (1,2) enabled
    }

    @Test
    void definitionReportHasNoLengthPrefix() {
        byte[] req = request(17, 1);
        svc.executeTc(addTc(1, req));
        sim.tm.clear();
        svc.executeTc(simpleTc(10, new byte[] { 0 })); // N=0: all

        byte[] tm = findTm(19, 11);
        ByteBuffer bb = ByteBuffer.wrap(tm, PusTmPacket.DATA_OFFSET, 6 + req.length);
        assertEquals(1, bb.get());
        assertEquals(APID, bb.getShort());
        assertEquals(1, bb.getShort());
        assertEquals(0, bb.get());
        byte[] reported = new byte[req.length];
        bb.get(reported);
        assertArrayEquals(req, reported);
    }

    private Pus19Service.EaDefinition def(int eventId) {
        return svc.definitions.get(Pus19Service.key(APID, eventId));
    }

    private static PusTcPacket simpleTc(int subtype, byte[] userData) {
        PusTcPacket p = new PusTcPacket(1, userData.length, 0, 19, subtype);
        p.getUserDataBuffer().put(userData);
        return p;
    }

    /** N x (app_process_id, event_def_id), all with app_process_id = MAIN_APID */
    private static byte[] ids(int... eventIds) {
        ByteBuffer bb = ByteBuffer.allocate(1 + 4 * eventIds.length);
        bb.put((byte) eventIds.length);
        for (int id : eventIds) {
            bb.putShort((short) APID);
            bb.putShort((short) id);
        }
        return bb.array();
    }

    /** a complete TC packet (with CRC), as embedded by the ground after post-processing */
    private static byte[] request(int type, int subtype) {
        return new PusTcPacket(APID, 1, 0, type, subtype).getBytes();
    }

    private static PusTcPacket addTc(int eventId, byte[] request) {
        return addTc(new int[] { eventId }, request);
    }

    /** TC[19,1]: N x (app_process_id, event_def_id, request) with no length prefix (§8.19.2.1) */
    private static PusTcPacket addTc(int[] eventIds, byte[]... requests) {
        int len = 1;
        for (byte[] r : requests) {
            len += 4 + r.length;
        }
        ByteBuffer ud = ByteBuffer.allocate(len);
        ud.put((byte) eventIds.length);
        for (int i = 0; i < eventIds.length; i++) {
            ud.putShort((short) APID);
            ud.putShort((short) eventIds[i]);
            ud.put(requests[i]);
        }
        return simpleTc(1, ud.array());
    }

    private List<Integer> nackStartCodes() {
        List<Integer> codes = new ArrayList<>();
        for (byte[] p : sim.tm) {
            if (u8(p, 7) == TM_TYPE_ACK && u8(p, 8) == NACK_START) {
                codes.add(org.yamcs.utils.ByteArrayUtils.decodeInt(p, PusTmPacket.DATA_OFFSET + 4));
            }
        }
        return codes;
    }

    private int count(int ackSubtype) {
        int n = 0;
        for (byte[] p : sim.tm) {
            if (u8(p, 7) == TM_TYPE_ACK && u8(p, 8) == ackSubtype) {
                n++;
            }
        }
        return n;
    }

    private byte[] findTm(int type, int subtype) {
        for (byte[] p : sim.tm) {
            if (u8(p, 7) == type && u8(p, 8) == subtype) {
                return p;
            }
        }
        throw new AssertionError("TM[" + type + "," + subtype + "] not sent");
    }

    private static int u8(byte[] b, int i) {
        return b[i] & 0xff;
    }
}
