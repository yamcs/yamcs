package org.yamcs.tctm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.utils.TimeEncoding;

public class TcpTcDataLinkTest {

    @BeforeClass
    public static void setup() throws IOException {
        TimeEncoding.setUp();
        int port = 10025;
        MyTcpServer mtc = (new TcpTcDataLinkTest()).new MyTcpServer(port);
        mtc.start();
    }

    public class MyTcpServer extends Thread {
        int port;
        ServerSocket serverSocket;

        public MyTcpServer(int port) throws IOException {
            this.port = port;
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(100000);

        }

        public void run() {
            Socket server = null;
            try {
                server = serverSocket.accept();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            int maxLength = 65542;
            while (true) {
                try {
                    DataInputStream in = new DataInputStream(server.getInputStream());
                    // while(in.available() <= 0) {}
                    byte hdr[] = new byte[6];
                    in.readFully(hdr);
                    int remaining = ((hdr[4] & 0xFF) << 8) + (hdr[5] & 0xFF) + 1;
                    if (remaining > maxLength - 6)
                        throw new IOException("Remaining packet length too big: " + remaining + " maximum allowed is "
                                + (maxLength - 6));
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
    }

    @Test
    public void testTcpTcMaxRate() throws ConfigurationException, InterruptedException, IOException {
        Map<Integer, Long> sentTime = new HashMap<>();
        List<Integer> successful = new ArrayList<>();
        List<Integer> failed = new ArrayList<>();
        int ncommands = 1000;
        // tcMaxRate = 25
        // tcQueueSize = 100
        TcpTcDataLink dataLink = new TcpTcDataLink("testinst", "test1", "testMaxRate");
        Semaphore semaphore = new Semaphore(0);
        
        dataLink.setCommandHistoryPublisher(new CommandHistoryPublisher() {
            
            @Override
            public void updateTimeKey(CommandId cmdId, String key, long value) {
                sentTime.put(cmdId.getSequenceNumber(), value);
            }

            @Override
            public void updateStringKey(CommandId cmdId, String key, String value) {
                if (key.equals("Acknowledge_Sent_Status")) {
                    if(value.equals("ACK: OK")) {
                        successful.add(cmdId.getSequenceNumber());
                    } else if(value.equals("NACK")) {
                        failed.add(cmdId.getSequenceNumber());
                    } else {
                        fail("Unexpected ack '"+value+"'");
                    }
                    semaphore.release();
                }
            }

            @Override
            public void publish(CommandId cmdId, String key, int value) {
                ;
            }

            @Override
            public void addCommand(PreparedCommand pc) {
                ;

            }
        });
        dataLink.startAsync();
        dataLink.awaitRunning();

        for (int i = 1; i <= ncommands; i++) {
            dataLink.sendTc(getCommand(i));
        }

        assertTrue(semaphore.tryAcquire(ncommands, 10, TimeUnit.SECONDS));
        assertTrue("Number of commands sent is smaller than queue size: ", successful.size() >= 100);
        for (int i = 5; i<successful.size()-25; i++) {
            int seq1 = successful.get(i);
            int seq2 = successful.get(i+25);
            long gap = sentTime.get(seq2) - sentTime.get(seq1);
            assertTrue("gap is not right: "+gap, gap>=990 && gap<1010);
        }
//     
 //       assertTrue("Number of commands sent is much bigger than queue size: ", sentCount < 120);

    }

    @Test
    public void testTcpTcDefault() throws ConfigurationException,   InterruptedException, IOException {
        Map<Integer, Long> sentTime = new HashMap<>();
        Map<Integer, String> sentStatus = new HashMap<>();

        TcpTcDataLink dataLink = new TcpTcDataLink("testinst", "test1", "test_default");
        Semaphore semaphore = new Semaphore(0);
        
        dataLink.setCommandHistoryPublisher(new CommandHistoryPublisher() {

            @Override
            public void updateTimeKey(CommandId cmdId, String key, long value) {
                sentTime.put(cmdId.getSequenceNumber(), value);
            }

            @Override
            public void updateStringKey(CommandId cmdId, String key, String value) {
                if (key.equals("Acknowledge_Sent_Status")) {
                    sentStatus.put(cmdId.getSequenceNumber(), value);
                    semaphore.release();
                }
            }

            @Override
            public void publish(CommandId cmdId, String key, int value) {
                ;
            }

            @Override
            public void addCommand(PreparedCommand pc) {
                ;
            }
        });
        dataLink.startAsync();
        dataLink.awaitRunning();
        
        
        for (int i = 1; i <= 1000; i++) {
            dataLink.sendTc(getCommand(i));
        }
        assertTrue(semaphore.tryAcquire(1000, 10, TimeUnit.SECONDS));
        for (int i = 1; i <= 1000; i++) {
            assertEquals("ACK: OK", sentStatus.get(i));
        }
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
}
