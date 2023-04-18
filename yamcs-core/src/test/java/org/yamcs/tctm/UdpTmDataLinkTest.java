package org.yamcs.tctm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.LoggingUtils;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.tctm.Link.Status;
import org.yamcs.utils.TimeEncoding;

public class UdpTmDataLinkTest {
    @BeforeAll
    public static void beforeClass() {
        EventProducerFactory.setMockup(false);
        TimeEncoding.setUp();
        LoggingUtils.configureLogging(Level.SEVERE);
    }

    @Test
    public void test1() throws Exception {
        ArrayBlockingQueue<TmPacket> pktQueue = new ArrayBlockingQueue<>(10);

        UdpTmDataLink link = new UdpTmDataLink();
        Map<String, Object> config = new HashMap<>();
        Random rand = new Random();
        int port = 20000 + rand.nextInt(10000);
        InetAddress addr = InetAddress.getByName("localhost");

        config.put("initialBytesToStrip", 3);
        config.put("port", port);
        config.put("checksum", port);
        link.init("test", "test", YConfiguration.wrap(config));
        link.setTmSink(p -> pktQueue.add(p));

        link.startAsync();
        link.awaitRunning();

        assertEquals(Status.OK, link.connectionStatus());

        DatagramSocket socket = new DatagramSocket();
        byte[] b1 = new byte[1003];
        rand.nextBytes(b1);
        DatagramPacket dp1 = new DatagramPacket(b1, b1.length, addr, port);
        socket.send(dp1);

        TmPacket pkt1 = pktQueue.poll(5, TimeUnit.SECONDS);
        assertNotNull(pkt1);

        assertEquals(1000, pkt1.length());
        for (int i = 0; i < pkt1.length(); i++) {
            assertEquals(b1[i + 3], pkt1.getPacket()[i]);
        }

        link.disable();
        Thread.sleep(1000);
        assertTrue(link.getDetailedStatus().contains("DISABLED"));

        link.enable();
        DatagramPacket dp2 = new DatagramPacket(b1, 2, addr, port);
        socket.send(dp2);
        TmPacket pkt2 = pktQueue.poll(1, TimeUnit.SECONDS);
        assertNull(pkt2);

        var extra = link.getExtraInfo();
        assertEquals(extra.get("Valid datagrams"), 1L);
        assertEquals(extra.get("Invalid datagrams"), 1L);

        socket.close();
        link.stopAsync();
        link.awaitTerminated();
    }
}
