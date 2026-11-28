package org.yamcs.simulator.cfdp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.cfdp.pdu.AckPacket;
import org.yamcs.cfdp.pdu.AckPacket.FileDirectiveSubtypeCode;
import org.yamcs.cfdp.pdu.AckPacket.TransactionStatus;
import org.yamcs.cfdp.pdu.CfdpHeader;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.cfdp.pdu.EofPacket;
import org.yamcs.cfdp.pdu.FileDataPacket;
import org.yamcs.cfdp.pdu.FileDirectiveCode;
import org.yamcs.cfdp.pdu.FinishedPacket;
import org.yamcs.cfdp.pdu.FinishedPacket.FileStatus;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.cfdp.pdu.NakPacket;
import org.yamcs.cfdp.pdu.SegmentRequest;
import org.yamcs.simulator.AbstractSimulator;
import org.yamcs.simulator.SimulatorCcsdsPacket;
import org.yamcs.simulator.TcpTmTcLink;

public class CfdpSenderTest {
    static final int FILE_SIZE = 3500; // 4 File Data PDUs

    BlockingQueue<CfdpPacket> sent = new LinkedBlockingQueue<>();
    File file;
    CfdpSender sender;
    CfdpHeader toSenderHeader;

    @BeforeEach
    void setup() throws Exception {
        file = Files.createTempFile("cfdp-sender-test", ".bin").toFile();
        byte[] data = new byte[FILE_SIZE];
        new Random(1).nextBytes(data);
        Files.write(file.toPath(), data);

        sender = new CfdpSender(new TestSim(), 11, file, "dest", null, new int[0]);
        CfdpHeader h = sender.directiveHeader;
        toSenderHeader = new CfdpHeader(true, true, true, false, h.getEntityIdLength(), h.getSequenceNumberLength(),
                h.getSourceId(), h.getDestinationId(), h.getSequenceNumber());
    }

    @AfterEach
    void cleanup() {
        file.delete();
    }

    @Test
    void testSuspendDuringData() throws Exception {
        sender.start();
        assertInstanceOf(MetadataPacket.class, next());
        assertInstanceOf(FileDataPacket.class, next());

        sender.suspend();
        sync();
        sent.clear();
        assertNothingSent(500);

        // a NAK received while suspended is queued
        receive(new NakPacket(0, 1000, List.of(new SegmentRequest(0, 1000)), toSenderHeader));
        assertNothingSent(300);

        sender.resume();
        List<Long> offsets = new ArrayList<>();
        CfdpPacket p;
        while (!((p = next()) instanceof EofPacket)) {
            offsets.add(((FileDataPacket) p).getOffset());
        }
        // the remaining data is sent before the EOF, then the NAKed segment
        assertEquals(FILE_SIZE, ((EofPacket) p).getFileLength());
        assertTrue(offsets.size() >= 2 && offsets.get(offsets.size() - 1) == 3000, "offsets: " + offsets);
        assertEquals(0, ((FileDataPacket) next()).getOffset());

        finish();
    }

    @Test
    void testSuspendStopsEofRetransmission() throws Exception {
        sender.start();
        waitFor(EofPacket.class);

        sender.suspend();
        sync();
        sent.clear();
        // the EOF is resent every 2 seconds when not suspended
        assertNothingSent(2500);

        sender.resume();
        waitFor(EofPacket.class);

        receive(new AckPacket(FileDirectiveCode.EOF, FileDirectiveSubtypeCode.FINISHED_BY_WAYPOINT_OR_OTHER,
                ConditionCode.NO_ERROR, TransactionStatus.ACTIVE, toSenderHeader));
        sync();
        sender.suspend();
        sender.resume();
        sync();
        // the EOF has been acknowledged, it is not resent after resuming
        assertNothingSent(500);

        finish();
    }

    @Test
    void testEofAckLimitSuspends() throws Exception {
        sender.start();
        waitFor(EofPacket.class);
        // the EOF is sent EOF_ACK_LIMIT - 1 times, 2 seconds apart, then the transaction is suspended
        for (int i = 1; i < CfdpSender.EOF_ACK_LIMIT - 1; i++) {
            waitFor(EofPacket.class, 3000);
        }
        for (int i = 0; i < 30 && !sender.isSuspended(); i++) {
            Thread.sleep(100);
        }
        assertTrue(sender.isSuspended());

        sender.resume();
        waitFor(EofPacket.class);
        finish();
    }

    private void finish() throws Exception {
        receive(new FinishedPacket(ConditionCode.NO_ERROR, true, FileStatus.SUCCESSFUL_RETENTION, null,
                toSenderHeader));
        AckPacket ack = waitFor(AckPacket.class);
        assertEquals(FileDirectiveCode.FINISHED, ack.getDirectiveCode());
        // suspend/resume are ignored once finished
        sender.suspend();
        sync();
        assertTrue(!sender.isSuspended());
    }

    private void receive(CfdpPacket packet) {
        sender.processCfdp(ByteBuffer.wrap(packet.toByteArray()));
    }

    private void sync() throws Exception {
        CfdpSender.executor.submit(() -> {
        }).get();
    }

    private CfdpPacket next() throws InterruptedException {
        CfdpPacket p = sent.poll(5, TimeUnit.SECONDS);
        if (p == null) {
            throw new AssertionError("no PDU sent");
        }
        return p;
    }

    private <T extends CfdpPacket> T waitFor(Class<T> type) throws InterruptedException {
        return waitFor(type, 5000);
    }

    private <T extends CfdpPacket> T waitFor(Class<T> type, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        List<CfdpPacket> skipped = new ArrayList<>();
        long t;
        while ((t = deadline - System.currentTimeMillis()) > 0) {
            CfdpPacket p = sent.poll(t, TimeUnit.MILLISECONDS);
            if (type.isInstance(p)) {
                return type.cast(p);
            } else if (p != null) {
                skipped.add(p);
            }
        }
        throw new AssertionError("Timeout waiting for " + type.getSimpleName() + "; received: " + skipped);
    }

    private void assertNothingSent(long ms) throws InterruptedException {
        assertNull(sent.poll(ms, TimeUnit.MILLISECONDS));
    }

    class TestSim extends AbstractSimulator {
        @Override
        public void transmitCfdp(CfdpPacket packet) {
            sent.add(packet);
        }

        @Override
        public int maxTmDataSize() {
            return 4096;
        }

        @Override
        protected void processTc(SimulatorCcsdsPacket tc) {
        }

        @Override
        protected void setTmLink(TcpTmTcLink tmLink) {
        }

        @Override
        protected void setTm2Link(TcpTmTcLink tm2Link) {
        }

        @Override
        protected void setLosLink(TcpTmTcLink losLink) {
        }

        @Override
        protected void doStart() {
        }

        @Override
        protected void doStop() {
        }
    }
}
