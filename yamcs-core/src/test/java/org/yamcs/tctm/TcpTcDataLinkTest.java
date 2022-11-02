package org.yamcs.tctm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.AbstractProcessorService;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.utils.TimeEncoding;

public class TcpTcDataLinkTest {
    MyTcpServer mtc;

    @BeforeAll
    public static void beforeClass() throws IOException {
        TimeEncoding.setUp();
        EventProducerFactory.setMockup(false);
    }

    @BeforeEach
    public void setupTcpServer() throws IOException {
        mtc = new MyTcpServer();
        mtc.start();
    }

    @AfterEach
    public void shutdownTcpServer() throws IOException {
        mtc = new MyTcpServer();
        mtc.quit();
    }

    static public class MyTcpServer extends Thread {
        int port;
        ServerSocket serverSocket;
        volatile boolean quitting = false;

        public MyTcpServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.bind(null);
            port = serverSocket.getLocalPort();
            serverSocket.setSoTimeout(100000);
        }

        @Override
        public void run() {
            Socket server = null;
            try {
                server = serverSocket.accept();
            } catch (IOException e1) {
                e1.printStackTrace();
                return;
            }
            int maxLength = 65542;
            while (!quitting) {
                try {
                    DataInputStream in = new DataInputStream(server.getInputStream());
                    // while(in.available() <= 0) {}
                    byte hdr[] = new byte[6];
                    in.readFully(hdr);
                    int remaining = ((hdr[4] & 0xFF) << 8) + (hdr[5] & 0xFF) + 1;
                    if (remaining > maxLength - 6) {
                        throw new IOException("Remaining packet length too big: " + remaining + " maximum allowed is "
                                + (maxLength - 6));
                    }
                    byte[] b = new byte[6 + remaining];
                    System.arraycopy(hdr, 0, b, 0, 6);
                    in.readFully(b, 6, remaining);

                } catch (SocketTimeoutException s) {
                    break;
                } catch (IOException e) {
                    break;
                }
            }
            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void quit() throws IOException {
            this.quitting = true;
            serverSocket.close();
        }
    }

    @Test
    public void testTcpTcMaxRate() throws ConfigurationException, InterruptedException, IOException {
        int ncommands = 20;
        int tcMaxRate = 2;

        Map<String, Object> config = new HashMap<>();
        config.put("tcMaxRate", tcMaxRate);
        config.put("tcQueueSize", ncommands);
        config.put("host", "localhost");
        config.put("port", mtc.port);
        config.put("commandPostprocessorClassName", GenericCommandPostprocessor.class.getName());

        TcpTcDataLink dataLink = new TcpTcDataLink();
        dataLink.init("testinst", "test1", YConfiguration.wrap(config));
        Semaphore semaphore = new Semaphore(0);
        MyCmdHistPublisher mypub = new MyCmdHistPublisher(semaphore);
        dataLink.setCommandHistoryPublisher(mypub);

        dataLink.startAsync();
        dataLink.awaitRunning();

        for (int i = 1; i <= ncommands; i++) {
            dataLink.sendCommand(getCommand(i));
        }

        assertTrue(semaphore.tryAcquire(ncommands, 10, TimeUnit.SECONDS));
        assertTrue(mypub.successful.size() >= ncommands, "Number of commands sent is smaller than queue size");
        for (int i = 5; i < mypub.successful.size() - tcMaxRate; i++) {
            int seq1 = mypub.successful.get(i);
            int seq2 = mypub.successful.get(i + tcMaxRate);
            long gap = mypub.sentTime.get(seq2) - mypub.sentTime.get(seq1);
            assertTrue(gap >= 850 && gap < 1150, "gap is not right: " + gap);
        }
        dataLink.stopAsync();
    }

    @Test
    public void testTcpTcDefault() throws ConfigurationException, InterruptedException, IOException {
        Map<String, Object> config = new HashMap<>();
        config.put("host", "localhost");
        config.put("port", mtc.port);
        config.put("commandPostprocessorClassName", GenericCommandPostprocessor.class.getName());

        TcpTcDataLink dataLink = new TcpTcDataLink();
        dataLink.init("testinst", "test1", YConfiguration.wrap(config));
        Semaphore semaphore = new Semaphore(0);
        MyCmdHistPublisher mypub = new MyCmdHistPublisher(semaphore);
        dataLink.setCommandHistoryPublisher(mypub);

        dataLink.startAsync();
        dataLink.awaitRunning();

        for (int i = 1; i <= 1000; i++) {
            dataLink.sendCommand(getCommand(i));
        }
        assertTrue(semaphore.tryAcquire(1000, 300, TimeUnit.SECONDS));
        assertEquals(1000, mypub.successful.size());
        dataLink.stopAsync();
    }

    private PreparedCommand getCommand(int seq) {

        CommandId cmdId = CommandId.newBuilder().setCommandName("/YSS/SIMULATOR/SWITCH_VOLTAGE_ON").setOrigin("Test")
                .setSequenceNumber(seq).setGenerationTime(System.currentTimeMillis()).build();
        PreparedCommand pc = new PreparedCommand(cmdId);

        byte[] b = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 1, 2, 3, 4, 5, 6, 7, 8, 9, 1, 2, 3, 4, 5, 6, 7, 8, 9, 1, 2, 3, 4, 5, 6,
                7, 8, 9, 1, 2, 3, 4, 5, 6, 7, 8, 9, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
        pc.setBinary(b);
        return pc;
    }

    public static class MyCmdHistPublisher extends AbstractProcessorService implements CommandHistoryPublisher {
        Map<Integer, Long> sentTime = new HashMap<>();
        List<Integer> successful = new ArrayList<>();
        List<Integer> failed = new ArrayList<>();
        Semaphore semaphore;

        public MyCmdHistPublisher(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void publish(CommandId cmdId, String key, long value) {
            sentTime.put(cmdId.getSequenceNumber(), value);
        }

        @Override
        public void publish(CommandId cmdId, String key, String value) {
            if (key.equals("Acknowledge_Sent_Status")) {
                if (value.equals("OK")) {
                    successful.add(cmdId.getSequenceNumber());
                } else if (value.equals("NOK")) {
                    failed.add(cmdId.getSequenceNumber());
                } else {
                    fail("Unexpected ack '" + value + "'");
                }
                semaphore.release();
            }
        }

        @Override
        public void publish(CommandId cmdId, String key, int value) {
        }

        @Override
        public void publish(CommandId cmdId, String key, byte[] binary) {
        }

        @Override
        public void addCommand(PreparedCommand pc) {
        }

        @Override
        protected void doStart() {

        }

        @Override
        protected void doStop() {
        }
    }
}
