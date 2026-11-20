package org.yamcs.simulator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.simulator.UdpTcFrameLink.TcEncoding;
import org.yamcs.simulator.UdpTmFrameLink.TmVcSender;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.utils.ByteArrayUtils;

class PusFrameLinkTest {
    @Test
    void ignoresRawDatagramShorterThanFrameHeader() {
        var link = new UdpTcFrameLink(null, 0, null, TcPacketFactory.PUS_PACKET_FACTORY, TcEncoding.RAW);
        link.processDatagram(new byte[2], 0, 2);
        assertEquals(0x01000000, link.getClcw());
    }

    @Test
    void idleTmCarriesCurrentOcf() {
        var sender = new TmVcSender(1, 0, 32, true, null);
        sender.setCLCW(0x0100062A);
        byte[] frame = sender.getIdleFrame();
        assertEquals(0x07FE, ByteArrayUtils.decodeUnsignedShort(frame, 4));
        assertEquals(0x0100062A, ByteArrayUtils.decodeInt(frame, 26));
    }

    @Test
    void tmWithoutCrcOrOcfKeepsTheWholeDataField() throws Exception {
        var sender = new TmVcSender(1, 0, 32, false, false, null);
        byte[] packet = new byte[26];
        Arrays.fill(packet, (byte) 0x42);
        sender.queue.add(packet);
        sender.dequeue();

        byte[] frame = sender.getFrame();
        assertEquals(0x0010, ByteArrayUtils.decodeUnsignedShort(frame, 0));
        assertEquals(0, ByteArrayUtils.decodeUnsignedShort(frame, 4));
        assertArrayEquals(packet, Arrays.copyOfRange(frame, 6, 32));
    }

    @Test
    void tmWithCrcAndOcfLeavesRoomForBoth() throws Exception {
        var sender = new TmVcSender(1, 0, 32, true, true, null);
        byte[] packet = new byte[20];
        Arrays.fill(packet, (byte) 0x42);
        sender.queue.add(packet);
        sender.dequeue();
        sender.setCLCW(0x0100062A);

        byte[] frame = sender.getFrame();
        assertEquals(0x0011, ByteArrayUtils.decodeUnsignedShort(frame, 0));
        assertArrayEquals(packet, Arrays.copyOfRange(frame, 6, 26));
        assertEquals(0x0100062A, ByteArrayUtils.decodeInt(frame, 26));
        assertEquals(new CrcCciitCalculator().compute(frame, 0, 30), ByteArrayUtils.decodeUnsignedShort(frame, 30));
    }

    @Test
    void rawTcUsesConfiguredFrameSizeSpacecraftAndCrc() {
        var simulator = new RecordingSimulator();
        var noCrc = new UdpTcFrameLink(simulator, 0, null, TcPacketFactory.PUS_PACKET_FACTORY, TcEncoding.RAW,
                false, 20, 1);
        byte[] packet = { 0x10, 1, (byte) 0xC0, 0, 0, 8, 0x20, 17, 1, 0, 0, 0, 0, 0, 0 };
        deliver(noCrc, tcFrame(packet, false, 2));
        deliver(noCrc, tcFrame(Arrays.copyOf(packet, 16), false, 1));
        assertEquals(0, simulator.commands);
        deliver(noCrc, tcFrame(packet, false, 1));
        assertEquals(1, simulator.commands);
        assertArrayEquals(packet, simulator.lastCommand);

        var crc = new UdpTcFrameLink(simulator, 0, null, TcPacketFactory.PUS_PACKET_FACTORY, TcEncoding.RAW,
                true, 22, 1);
        byte[] checksummed = tcFrame(packet, true, 1);
        checksummed[checksummed.length - 1] ^= 1;
        deliver(crc, checksummed);
        assertEquals(1, simulator.commands);
        deliver(crc, tcFrame(packet, true, 1));
        assertEquals(2, simulator.commands);
        assertArrayEquals(packet, simulator.lastCommand);
    }

    @Test
    void rawTcFrameDeliversEachPusPacketSeparately() {
        var simulator = new RecordingSimulator();
        var link = new UdpTcFrameLink(simulator, 0, null, TcPacketFactory.PUS_PACKET_FACTORY, TcEncoding.RAW,
                true, 64, 1);
        byte[] first = { 0x18, 1, (byte) 0xC0, 0, 0, 8, 0x20, 17, 1, 0, 0, 0, 0, 0, 0 };
        byte[] second = { 0x18, 1, (byte) 0xC0, 1, 0, 9, 0x20, 25, 2, 0, 0, 0, 0, 0, 0, 1 };
        byte[] data = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, data, first.length, second.length);

        deliver(link, tcFrame(data, true, 1));

        assertEquals(2, simulator.packets.size());
        assertArrayEquals(first, simulator.packets.get(0));
        assertArrayEquals(second, simulator.packets.get(1));
    }

    private static void deliver(UdpTcFrameLink link, byte[] frame) {
        link.processDatagram(frame, 0, frame.length);
    }

    private static byte[] tcFrame(byte[] data, boolean crc, int scid) {
        var frame = new byte[5 + data.length + (crc ? 2 : 0)];
        ByteArrayUtils.encodeUnsignedShort(0x2000 | scid, frame, 0);
        ByteArrayUtils.encodeUnsignedShort(frame.length - 1, frame, 2);
        System.arraycopy(data, 0, frame, 5, data.length);
        if (crc) {
            int offset = frame.length - 2;
            ByteArrayUtils.encodeUnsignedShort(new CrcCciitCalculator().compute(frame, 0, offset), frame, offset);
        }
        return frame;
    }

    private static class RecordingSimulator extends AbstractSimulator {
        int commands;
        byte[] lastCommand;
        final List<byte[]> packets = new ArrayList<>();

        @Override
        protected void processTc(SimulatorCcsdsPacket tc) {
            commands++;
            lastCommand = tc.getBytes();
            packets.add(lastCommand);
        }

        @Override
        protected void setTmLink(TcpTmTcLink link) {
        }

        @Override
        protected void setTm2Link(TcpTmTcLink link) {
        }

        @Override
        protected void setLosLink(TcpTmTcLink link) {
        }

        @Override
        public void transmitCfdp(CfdpPacket packet) {
        }

        @Override
        public int maxTmDataSize() {
            return 1500;
        }

        @Override
        protected void doStart() {
            notifyStarted();
        }

        @Override
        protected void doStop() {
            notifyStopped();
        }
    }
}
