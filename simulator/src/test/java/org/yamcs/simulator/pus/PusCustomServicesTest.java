package org.yamcs.simulator.pus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yamcs.utils.BitBuffer;
import org.yamcs.utils.TimeEncoding;

class PusCustomServicesTest {
    @TempDir
    File dataDirectory;
    ReportSimulator simulator;

    @BeforeEach
    void start() {
        TimeEncoding.setUp();
        simulator = new ReportSimulator(dataDirectory);
        simulator.doStart();
    }

    @AfterEach
    void stop() {
        simulator.doStop();
    }

    @Test
    void reportsTleBytesAndBothPackedChecksums() throws InterruptedException {
        byte[] tle = ("1" + " ".repeat(67) + "1" + "2-" + " ".repeat(66) + "0")
                .getBytes(StandardCharsets.US_ASCII);
        command(128, 1, tle);
        command(128, 2, new byte[0]);
        byte[] reported = new byte[tle.length];
        report(128, 3).getUserDataBuffer().get(reported);
        assertArrayEquals(tle, reported);

        command(128, 4, new byte[0]);
        byte[] checksums = new byte[3];
        report(128, 5).getUserDataBuffer().get(checksums);
        assertArrayEquals(new byte[] { 1, (byte) 0x81, (byte) 0x80 }, checksums);
    }

    @Test
    void reportsCommandedBeaconAndTransitionBackToNtm() throws InterruptedException {
        command(130, 1, new byte[] { 0, 30 });
        command(130, 3, new byte[0]);
        assertEquals(1, report(130, 4).getUserDataBuffer().get());

        command(130, 2, new byte[0]);
        command(130, 3, new byte[0]);
        assertEquals(0, report(130, 4).getUserDataBuffer().get());
    }

    @Test
    void rejectsWholeTableEditWhenOneSidIsUnrecognised() throws InterruptedException {
        byte[] data = new byte[9];
        BitBuffer bits = new BitBuffer(data);
        bits.putBits(0, 8); // STM
        bits.putBits(2, 8);
        bits.putBits(0, 8);
        bits.putBits(PusSimulator.MAIN_APID, 11);
        bits.putBits(1, 8);
        bits.putBits(0, 8);
        bits.putBits(PusSimulator.MAIN_APID, 11);
        bits.putBits(250, 8);

        command(130, 5, data);
        command(130, 7, new byte[] { 1, 0 });
        ByteBuffer table = report(130, 8).getUserDataBuffer();
        assertEquals(1, table.get());
        assertEquals(0, table.get());
        assertEquals(0, table.get());
    }

    @Test
    void tableReportPagesHaveOneBasedPageNumberAndTotal() throws InterruptedException {
        simulator.tmBudget = 24;
        byte[] request = new byte[26];
        request[0] = 25; // 25 requests for the empty STM table, two bytes per result
        command(130, 7, request);

        int[] rowsByPage = { 11, 11, 3 };
        for (int i = 0; i < rowsByPage.length; i++) {
            PusTmPacket page = report(130, 8);
            ByteBuffer raw = ByteBuffer.wrap(page.getBytes());
            assertEquals(i + 1, raw.getShort(page.getUserDataOffset() - 4));
            assertEquals(3, raw.getShort(page.getUserDataOffset() - 2));
            assertEquals(rowsByPage[i], page.getUserDataBuffer().get());
        }
    }

    @Test
    void overlayClaimStoresInNtmAndExpiresEvenDuringCtm() throws Exception {
        byte[] add = new byte[8];
        BitBuffer request = new BitBuffer(add);
        request.putBits(10, 8); // Known ST[05] event with no periodic demo emission
        request.putBits(2, 16);
        request.putBits(1, 8);
        request.putBits(0, 8);
        request.putBits(PusSimulator.MAIN_APID, 11);
        request.putBits(1, 8);
        command(130, 9, add);
        command(130, 11, new byte[] { 1, 10 });
        command(130, 15, new byte[0]);
        assertEquals(0b10, overlayStatusFlags());

        simulator.executor.submit(() -> simulator.pus5Service.raiseEvent(10, 1, new byte[0])).get(2, TimeUnit.SECONDS);
        command(130, 17, new byte[0]);
        assertEquals(0b110, overlayClaimFlags()); // generate, store
        command(130, 19, new byte[] { 0, 20 });
        command(130, 17, new byte[0]);
        assertEquals(0b111, overlayClaimFlags()); // generate, store, transmit

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        do {
            command(130, 15, new byte[0]);
            if (overlayStatusFlags() == 0b10) {
                return;
            }
        } while (System.nanoTime() < deadline);
        fail("Expected timed overlay to expire during CTM");
    }

    @Test
    void abortingDumpRetainsContactModeButClearsDumpActive() throws InterruptedException {
        command(130, 20, new byte[] { 0, 30, 0, 10, 0, 5, 1, 1, 1, 0, 5 });
        command(130, 21, new byte[0]);
        ByteBuffer beforeAbort = report(130, 22).getUserDataBuffer();
        assertEquals(2, beforeAbort.get());
        assertEquals(0x80, beforeAbort.get(simulator.timeEncoding.getEncodedLength() + 1) & 0x80);

        command(130, 23, new byte[0]);
        command(130, 21, new byte[0]);
        ByteBuffer afterAbort = report(130, 22).getUserDataBuffer();
        assertEquals(2, afterAbort.get());
        assertEquals(0, afterAbort.get(simulator.timeEncoding.getEncodedLength() + 1) & 0x80);
    }

    @Test
    void roundTripsEightBitStructureAndSixteenBitParameterIdentifiers() throws InterruptedException {
        command(3, 1, new byte[] {
                (byte) 145, 0, 0, 3, (byte) 232, 2, 1, 2, (byte) 254, (byte) 220,
                1, 3, 1, (byte) 208, (byte) 160
        });
        command(3, 9, new byte[] { 1, (byte) 145 });
        byte[] expected = {
                (byte) 145, 0, 0, 0, 3, (byte) 232, 2, 1, 2, (byte) 254, (byte) 220,
                1, 3, 1, (byte) 208, (byte) 160
        };
        byte[] actual = new byte[expected.length];
        report(3, 10).getUserDataBuffer().get(actual);
        assertArrayEquals(expected, actual);

        command(3, 27, new byte[] { 1, (byte) 145 });
        ByteBuffer oneShot = report(3, 25).getUserDataBuffer();
        assertEquals(145, oneShot.get() & 0xFF);
        assertEquals(20, oneShot.remaining());
    }

    private void command(int service, int subtype, byte[] data) {
        var packet = new PusTcPacket(PusSimulator.MAIN_APID, data.length, 15, service, subtype);
        packet.getUserDataBuffer().put(data);
        packet.fillChecksum();
        simulator.processTc(packet);
    }

    private int overlayStatusFlags() throws InterruptedException {
        BitBuffer status = PusPackedFields.readBuffer(report(130, 16).getUserDataBuffer());
        assertEquals(1, status.getBits(8));
        assertEquals(10, status.getBits(8));
        return (int) status.getBits(2);
    }

    private int overlayClaimFlags() throws InterruptedException {
        BitBuffer claims = PusPackedFields.readBuffer(report(130, 18).getUserDataBuffer());
        assertEquals(1, claims.getBits(8));
        assertEquals(0, claims.getBits(8));
        assertEquals(PusSimulator.MAIN_APID, claims.getBits(11));
        assertEquals(1, claims.getBits(8));
        assertEquals(8, claims.getBits(8));
        return (int) claims.getBits(3);
    }

    private PusTmPacket report(int service, int subtype) throws InterruptedException {
        PusTmPacket packet = simulator.reports.poll(3, TimeUnit.SECONDS);
        assertNotNull(packet, "Expected TM[" + service + "," + subtype + "]");
        assertEquals(service, packet.getType());
        assertEquals(subtype, packet.getSubtype());
        return packet;
    }

    private static class ReportSimulator extends PusSimulator {
        final LinkedBlockingQueue<PusTmPacket> reports = new LinkedBlockingQueue<>();
        volatile int tmBudget = 1500;

        ReportSimulator(File directory) {
            super(directory);
        }

        @Override
        public int maxTmDataSize() {
            return tmBudget;
        }

        @Override
        void transmitRealtimeTM(PusTmPacket packet) {
            if (packet.getType() == 128 || packet.getType() == 130
                    || (packet.getType() == 3 && (packet.getSubtype() == 10
                            || (packet.getSubtype() == 25 && (packet.getUserDataBuffer().get(0) & 0xFF) == 145)))) {
                reports.add(packet);
            }
        }
    }
}
