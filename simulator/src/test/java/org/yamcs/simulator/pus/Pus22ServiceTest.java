package org.yamcs.simulator.pus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.simulator.SimulatorCcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;

public class Pus22ServiceTest {

    static final int TM_TYPE_ACK = 1;
    static final int ACK_START = 3;
    static final int NACK_START = 4;
    static final int ACK_COMPLETION = 7;

    // short orbital period so tests don't have to wait a realistic 90 minutes
    static final long TEST_ORBIT_PERIOD_NANOS = TimeUnit.MILLISECONDS.toNanos(500);

    TestSim sim;
    Pus22Service svc;

    @BeforeAll
    static void setupTime() {
        org.yamcs.utils.TimeEncoding.setUp();
    }

    @BeforeEach
    void setup() throws Exception {
        File dir = Files.createTempDirectory("pus22test").toFile();
        dir.deleteOnExit();
        sim = new TestSim(dir);
        svc = new Pus22Service(sim, TEST_ORBIT_PERIOD_NANOS);
        svc.start();
    }

    @AfterEach
    void teardown() {
        sim.executor.shutdownNow();
    }

    @Test
    void insertIntoUnknownGroupIsRejected() {
        svc.executeTc(insertTc(1, 5, positionInMillis(3_600_000)));

        byte[] nack = lastAck(NACK_START);
        assertEquals(AbstractPusService.START_ERR_UNKNOWN_GROUP, ackCode(nack));
        assertNull(lastAck(ACK_START));
        assertTrue(sim.released.isEmpty());
    }

    @Test
    void fullLifecycleReleasesWhenGroupAndSubscheduleEnabled() throws Exception {
        // create group 2 enabled
        svc.executeTc(createGroupsTc(new int[] { 2 }, new int[] { 1 }));
        assertNotNull(lastAck(ACK_COMPLETION));

        svc.executeTc(insertTc(1, 2, positionInMillis(400)));
        // sub-schedule 1 was auto-created disabled (ECSS 6.22.6.6j.1b) -> enable it
        svc.executeTc(simpleTc(20, new byte[] { 1 }));

        Thread.sleep(900);
        assertEquals(1, sim.released.size());
        assertEquals(17, sim.released.get(0).getType()); // the embedded TC[17,1]
    }

    @Test
    void disabledGroupWithholdsRelease() throws Exception {
        svc.executeTc(createGroupsTc(new int[] { 3 }, new int[] { 0 })); // group 3 disabled
        svc.executeTc(insertTc(1, 3, positionInMillis(400)));
        svc.executeTc(simpleTc(20, new byte[] { 1 })); // enable sub-schedule 1

        Thread.sleep(900);
        assertTrue(sim.released.isEmpty());
    }

    @Test
    void maxSubschedulesRejected() {
        svc.executeTc(createGroupsTc(new int[] { 0 }, new int[] { 1 }));
        for (int i = 0; i < Pus22Service.MAX_SUBSCHEDULES; i++) {
            svc.executeTc(insertTc(i, 0, positionInMillis(3_600_000)));
        }
        assertNull(lastAck(NACK_START));

        svc.executeTc(insertTc(Pus22Service.MAX_SUBSCHEDULES, 0, positionInMillis(3_600_000)));
        assertEquals(AbstractPusService.START_ERR_MAX_SUBSCHEDULES_REACHED, ackCode(lastAck(NACK_START)));
    }

    @Test
    void groupStatusReport() {
        svc.executeTc(createGroupsTc(new int[] { 4, 9 }, new int[] { 1, 0 }));
        sim.tm.clear();
        svc.executeTc(simpleTc(26, new byte[0]));

        byte[] report = null;
        for (byte[] p : sim.tm) {
            if (u8(p, 7) == 22 && u8(p, 8) == 27) {
                report = p;
            }
        }
        assertNotNull(report);
        int off = 21; // PusTmPacket.DATA_OFFSET
        assertEquals(2, ByteArrayUtils.decodeInt(report, off));
        // two (id,status) pairs follow
        int a = u8(report, off + 4);
        int b = u8(report, off + 6);
        assertTrue((a == 4 && b == 9) || (a == 9 && b == 4));
    }

    @Test
    void createDuplicateGroupRejected() {
        svc.executeTc(createGroupsTc(new int[] { 1 }, new int[] { 1 }));
        sim.tm.clear();
        svc.executeTc(createGroupsTc(new int[] { 1 }, new int[] { 1 }));
        assertEquals(AbstractPusService.START_ERR_GROUP_EXISTS, ackCode(lastAck(NACK_START)));
    }

    @Test
    void deleteGroupWithActivitiesRejected() {
        svc.executeTc(createGroupsTc(new int[] { 6 }, new int[] { 1 }));
        svc.executeTc(insertTc(1, 6, positionInMillis(3_600_000)));
        sim.tm.clear();
        svc.executeTc(deleteGroupsTc(new int[] { 6 }));
        assertEquals(AbstractPusService.START_ERR_GROUP_HAS_ACTIVITIES, ackCode(lastAck(NACK_START)));
    }

    @Test
    void setOrbitNumberAppliesOnlyAtNextWrap() throws Exception {
        svc.executeTc(setOrbitNumberTc(1000));
        assertNotNull(lastAck(ACK_COMPLETION));

        // not applied yet - still well within the current (short, 500ms) orbit
        Thread.sleep(50);
        assertNotEquals(1000L, svc.currentPosition().orbitNumber);

        // after a full orbit period the wrap must have happened and the override applied
        Thread.sleep(600);
        assertEquals(1000L, svc.currentPosition().orbitNumber);
    }

    // ---- helpers ----

    private byte[] positionInMillis(int millisAhead) {
        long deltaTicks = (long) millisAhead * 1_000_000L * PusPosition.TICKS_PER_ORBIT / TEST_ORBIT_PERIOD_NANOS;
        ByteBuffer bb = ByteBuffer.allocate(PusPosition.LENGTH_BYTES);
        svc.currentPosition().shiftByTicks(deltaTicks).encode(bb);
        return bb.array();
    }

    private PusTcPacket simpleTc(int subtype, byte[] userData) {
        PusTcPacket p = new PusTcPacket(1, userData.length, 0, 22, subtype);
        p.getUserDataBuffer().put(userData);
        return p;
    }

    private PusTcPacket insertTc(int subschedule, int group, byte[] encodedPosition) {
        byte[] embedded = new PusTcPacket(1, 1, 0, 17, 1).getBytes();
        ByteBuffer ud = ByteBuffer.allocate(2 + 1 + encodedPosition.length + embedded.length);
        ud.put((byte) subschedule);
        ud.put((byte) 1); // N
        ud.put((byte) group);
        ud.put(encodedPosition);
        ud.put(embedded);
        return simpleTc(4, ud.array());
    }

    private PusTcPacket createGroupsTc(int[] ids, int[] statuses) {
        ByteBuffer ud = ByteBuffer.allocate(1 + ids.length * 2);
        ud.put((byte) ids.length);
        for (int i = 0; i < ids.length; i++) {
            ud.put((byte) ids[i]);
            ud.put((byte) statuses[i]);
        }
        return simpleTc(22, ud.array());
    }

    private PusTcPacket deleteGroupsTc(int[] ids) {
        ByteBuffer ud = ByteBuffer.allocate(1 + ids.length);
        ud.put((byte) ids.length);
        for (int id : ids) {
            ud.put((byte) id);
        }
        return simpleTc(23, ud.array());
    }

    private PusTcPacket setOrbitNumberTc(long newOrbitNumber) {
        ByteBuffer ud = ByteBuffer.allocate(4);
        ud.putInt((int) newOrbitNumber);
        return simpleTc(28, ud.array());
    }

    private static int u8(byte[] b, int i) {
        return b[i] & 0xff;
    }

    /** last ack/nack TM packet of the given subtype (type 1) */
    private byte[] lastAck(int subtype) {
        byte[] found = null;
        for (byte[] p : sim.tm) {
            if (u8(p, 7) == TM_TYPE_ACK && u8(p, 8) == subtype) {
                found = p;
            }
        }
        return found;
    }

    private static int ackCode(byte[] nack) {
        return ByteArrayUtils.decodeInt(nack, 21 + 4);
    }

    static class TestSim extends PusSimulator {
        final List<byte[]> tm = new ArrayList<>();
        final List<PusTcPacket> released = new ArrayList<>();

        TestSim(File dir) {
            super(dir);
            executor = new ScheduledThreadPoolExecutor(1);
        }

        @Override
        void transmitRealtimeTM(PusTmPacket packet) {
            packet.fillChecksum();
            tm.add(packet.getBytes().clone());
        }

        @Override
        public void processTc(SimulatorCcsdsPacket tc) {
            released.add((PusTcPacket) tc);
        }
    }
}
