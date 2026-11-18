package org.yamcs.simulator.pus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.simulator.pus.Pus11ServiceTest.TestSim;

public class Pus20ServiceTest {
    static final int DATA_OFFSET = 21; // PusTmPacket.DATA_OFFSET
    static final int TM_TYPE_ACK = 1;
    static final int NACK_START = 4;

    TestSim sim;
    Pus20Service svc;

    @BeforeAll
    static void setupTime() {
        org.yamcs.utils.TimeEncoding.setUp();
    }

    @BeforeEach
    void setup() throws Exception {
        File dir = Files.createTempDirectory("pus20test").toFile();
        dir.deleteOnExit();
        sim = new TestSim(dir);
        svc = new Pus20Service(sim);
    }

    @AfterEach
    void teardown() {
        sim.executor.shutdownNow();
    }

    @Test
    void reportSingle() {
        svc.executeTc(tc(1, new byte[] { 1, 3 })); // AdcsMode, enumerated uint8
        byte[] r = report();
        assertEquals(1, u8(r, DATA_OFFSET));
        assertEquals(3, u8(r, DATA_OFFSET + 1));
        assertEquals(2, u8(r, DATA_OFFSET + 2)); // SUN_POINTING
        assertNull(nack());
    }

    @Test
    void reportMultipleWithDeducedSizes() {
        svc.executeTc(tc(1, new byte[] { 4, 1, 2, 4, 5 }));
        byte[] r = report();
        ByteBuffer bb = ByteBuffer.wrap(r, DATA_OFFSET, r.length - DATA_OFFSET);
        assertEquals(4, bb.get());
        assertEquals(1, bb.get());
        assertEquals(1000, bb.getShort());
        assertEquals(2, bb.get());
        assertEquals(5.0f, bb.getFloat());
        assertEquals(4, bb.get());
        assertEquals(64000, bb.getInt());
        assertEquals(5, bb.get());
        assertEquals(1.25, bb.getDouble());
    }

    @Test
    void reportWithUnknownParameter() {
        svc.executeTc(tc(1, new byte[] { 3, 3, 99, 6 }));
        assertEquals(AbstractPusService.START_ERR_UNKNOWN_PARAMETER, nackCode());
        assertEquals(99, nackData()); // the failure data is the unknown parameter ID
        // the valid instructions are still processed
        byte[] r = report();
        ByteBuffer bb = ByteBuffer.wrap(r, DATA_OFFSET, r.length - DATA_OFFSET);
        assertEquals(2, bb.get());
        assertEquals(3, bb.get());
        assertEquals(2, bb.get());
        assertEquals(6, bb.get());
        assertEquals(-10, bb.getShort());
    }

    @Test
    void reportOnlyUnknownParameter() {
        svc.executeTc(tc(1, new byte[] { 1, 99 }));
        assertEquals(AbstractPusService.START_ERR_UNKNOWN_PARAMETER, nackCode());
        assertNull(report());
    }

    @Test
    void reportTruncated() {
        svc.executeTc(tc(1, new byte[] { 3, 1 }));
        assertEquals(AbstractPusService.START_ERR_INVALID_REQUEST, nackCode());
        assertNull(report());
    }

    @Test
    void setThenGet() {
        ByteBuffer bb = ByteBuffer.allocate(64);
        bb.put((byte) 4);
        bb.put((byte) 1).putShort((short) 250);
        bb.put((byte) 2).putFloat(-3.5f);
        bb.put((byte) 6).putShort((short) -300);
        bb.put((byte) 5).putDouble(2.75);
        svc.executeTc(tc(3, java.util.Arrays.copyOf(bb.array(), bb.position())));
        assertNull(nack());
        assertEquals(250, svc.getRawValue(1));
        assertEquals(-3.5f, Float.intBitsToFloat((int) svc.getRawValue(2)));
        assertEquals(-300, svc.getRawValue(6));
        assertEquals(2.75, Double.longBitsToDouble(svc.getRawValue(5)));

        sim.tm.clear();
        svc.executeTc(tc(1, new byte[] { 2, 6, 1 }));
        byte[] r = report();
        ByteBuffer rb = ByteBuffer.wrap(r, DATA_OFFSET, r.length - DATA_OFFSET);
        assertEquals(2, rb.get());
        assertEquals(6, rb.get());
        assertEquals(-300, rb.getShort());
        assertEquals(1, rb.get());
        assertEquals(250, rb.getShort());
    }

    @Test
    void setEnumAndBoolean() {
        // AdcsMode = 1 (DETUMBLE), PayloadPowerEnabled = 0 (DISABLED)
        svc.executeTc(tc(3, new byte[] { 2, 3, 1, 7, 0 }));
        assertNull(nack());
        assertEquals(1, svc.getRawValue(3));
        assertEquals(0, svc.getRawValue(7));

        sim.tm.clear();
        svc.executeTc(tc(1, new byte[] { 2, 7, 3 }));
        byte[] r = report();
        assertArrayEquals(new byte[] { 2, 7, 0, 3, 1 }, java.util.Arrays.copyOfRange(r, DATA_OFFSET, DATA_OFFSET + 5));
    }

    @Test
    void setWithUnknownParameter() {
        // the first instruction is valid, the second refers to an unknown parameter; the rest cannot be parsed
        svc.executeTc(tc(3, new byte[] { 3, 3, 3, 99, 1, 2, 4, 0, 0, 0, 9 }));
        assertEquals(AbstractPusService.START_ERR_UNKNOWN_PARAMETER, nackCode());
        assertEquals(99, nackData());
        assertEquals(3, svc.getRawValue(3)); // NADIR_POINTING, applied
        assertEquals(64000, svc.getRawValue(4)); // the instructions after the unknown parameter are ignored
    }

    @Test
    void setTruncated() {
        svc.executeTc(tc(3, new byte[] { 2, 3, 7, 1, 0 })); // the value of parameter 1 needs 2 bytes
        assertEquals(AbstractPusService.START_ERR_INVALID_REQUEST, nackCode());
        assertEquals(2, svc.getRawValue(3)); // nothing applied
    }

    @Test
    void invalidSubtype() {
        svc.executeTc(tc(6, new byte[0]));
        assertEquals(AbstractPusService.START_ERR_INVALID_PUS_SUBTYPE, nackCode());
    }

    private PusTcPacket tc(int subtype, byte[] userData) {
        PusTcPacket p = new PusTcPacket(1, userData.length, 0, 20, subtype);
        p.getUserDataBuffer().put(userData);
        return p;
    }

    private byte[] report() {
        byte[] found = null;
        for (byte[] p : sim.tm) {
            if (u8(p, 7) == 20 && u8(p, 8) == 2) {
                found = p;
            }
        }
        return found;
    }

    private byte[] nack() {
        byte[] found = null;
        for (byte[] p : sim.tm) {
            if (u8(p, 7) == TM_TYPE_ACK && u8(p, 8) == NACK_START) {
                found = p;
            }
        }
        return found;
    }

    private int nackData() {
        byte[] found = nack();
        assertNotNull(found);
        return u8(found, DATA_OFFSET + 8);
    }

    private int nackCode() {
        byte[] found = nack();
        assertNotNull(found);
        return org.yamcs.utils.ByteArrayUtils.decodeInt(found, DATA_OFFSET + 4);
    }

    private static int u8(byte[] b, int i) {
        return b[i] & 0xff;
    }
}
