package org.yamcs.tctm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;

public class UdpFrameTransportTest {

    @Test
    public void testTcTransportSendsOneDatagram() throws Exception {
        try (DatagramSocket receiver = new DatagramSocket(0)) {
            receiver.setSoTimeout(2000);
            var transport = new UdpTcFrameTransport();
            transport.init("test", "tc", YConfiguration.wrap(Map.of(
                    "host", "127.0.0.1", "port", receiver.getLocalPort())));
            transport.start();
            byte[] expected = { 1, 2, 3, 4 };
            transport.send(expected);

            DatagramPacket packet = new DatagramPacket(new byte[16], 16);
            receiver.receive(packet);
            assertArrayEquals(expected, Arrays.copyOf(packet.getData(), packet.getLength()));
            transport.stop();
        }
    }

    @Test
    public void testTmTransportStripsPrefix() throws Exception {
        int port;
        try (DatagramSocket probe = new DatagramSocket(0)) {
            port = probe.getLocalPort();
        }
        var transport = new UdpTmFrameTransport();
        transport.init("test", "tm", YConfiguration.wrap(Map.of(
                "port", port, "initialBytesToStrip", 2)));
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<byte[]> actual = new AtomicReference<>();
        transport.start(4, new TmFrameTransport.Receiver() {
            @Override
            public void onFrame(byte[] data, int offset, int length) {
                actual.set(Arrays.copyOfRange(data, offset, offset + length));
                received.countDown();
            }

            @Override
            public void onFailure(Throwable cause) {
                received.countDown();
            }
        });

        try (DatagramSocket sender = new DatagramSocket()) {
            byte[] datagram = { 9, 9, 1, 2, 3, 4 };
            sender.send(new DatagramPacket(datagram, datagram.length,
                    InetAddress.getLoopbackAddress(), port));
        }
        assertTrue(received.await(2, TimeUnit.SECONDS));
        assertArrayEquals(new byte[] { 1, 2, 3, 4 }, actual.get());
        transport.stop();
    }
}
