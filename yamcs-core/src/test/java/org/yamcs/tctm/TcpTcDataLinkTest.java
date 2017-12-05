package org.yamcs.tctm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
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
    int port=10025;
    MyTcpServer mtc = (new TcpTcDataLinkTest()).new MyTcpServer(port);
    mtc.start();
  }

  public class MyTcpServer extends Thread {
    int port;
    ServerSocket serverSocket;


    public MyTcpServer(int port) throws IOException {
      this.port=port;
      serverSocket=new ServerSocket(port);
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
      while(true) {
        try {
          DataInputStream in = new DataInputStream(server.getInputStream());
          while(in.available() <= 0) {}
          byte hdr[] = new byte[6];
          in.readFully(hdr);
          int remaining=((hdr[4]&0xFF)<<8)+(hdr[5]&0xFF)+1;
          if(remaining>maxLength-6) throw new IOException("Remaining packet length too big: "+remaining+" maximum allowed is "+(maxLength-6));
          byte[] b = new byte[6+remaining];
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
  public void testTcpTcMaxRate() throws ConfigurationException,
  InterruptedException, IOException {
    Map<Integer, Long> sentTime = new HashMap<>();
    Map<Integer, String> sentStatus = new HashMap<>();

    //tcMaxRate = 25
    //tcQueueSize = 100
    TcpTcDataLink dataLink = new TcpTcDataLink("testinst", "test1", "testMaxRate");

    dataLink.setCommandHistoryPublisher(new CommandHistoryPublisher() {

      @Override
      public void updateTimeKey(CommandId cmdId, String key, long value) {
        sentTime.put(cmdId.getSequenceNumber(), value);
      }

      @Override
      public void updateStringKey(CommandId cmdId, String key, String value) {
        if(key.equals("Acknowledge_Sent_Status"))
          sentStatus.put(cmdId.getSequenceNumber(), value);
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
    Thread.sleep(20000);

    for(int i=1; i <= 1000; i++) {
      dataLink.sendTc(getCommand(i));
    }
    
    Thread.sleep(10000);
    int sentCount = 0;
    long previous = 0;
    int expectedGap = 38; // 2ms tolerance
    for(int i=1; i <= 1000; i++) {
      if(sentStatus.get(i).equals("ACK: OK")) {
        sentCount++;
        long gap = sentTime.get(i) - previous;
        previous = sentTime.get(i);
        assertTrue("Wait between send commands too small: " + gap, gap >= expectedGap);
      }
    }   
    assertTrue("Number of commands sent are smaller than queue size: ", sentCount >= 100);


  }

  
  @Test
  public void testTcpTcDefault() throws ConfigurationException,
  InterruptedException, IOException {
    Map<Integer, Long> sentTime = new HashMap<>();
    Map<Integer, String> sentStatus = new HashMap<>();

    TcpTcDataLink dataLink = new TcpTcDataLink("testinst", "test1", "test_default");

    dataLink.setCommandHistoryPublisher(new CommandHistoryPublisher() {

      @Override
      public void updateTimeKey(CommandId cmdId, String key, long value) {
        sentTime.put(cmdId.getSequenceNumber(), value);
      }

      @Override
      public void updateStringKey(CommandId cmdId, String key, String value) {
        if(key.equals("Acknowledge_Sent_Status"))
          sentStatus.put(cmdId.getSequenceNumber(), value);
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
    Thread.sleep(20000);

    for(int i=1; i <= 1000; i++) {
      dataLink.sendTc(getCommand(i));
    }
    
    Thread.sleep(10000);
    for(int i=1; i <= 1000; i++) {
        assertEquals("ACK: OK", sentStatus.get(i));
    }   

  }

  
  

  public PreparedCommand getCommand(int seq) {

    CommandId cmdId = CommandId.newBuilder().setCommandName("/YSS/SIMULATOR/SWITCH_VOLTAGE_ON").setOrigin("Test")
        .setSequenceNumber(seq).setGenerationTime(System.currentTimeMillis()).build();
    PreparedCommand pc = new PreparedCommand(cmdId);

    byte[] b = {1,2,3,4,5,6,7,8,9,1,2,3,4,5,6,7,8,9,1,2,3,4,5,6,7,8,9,1,2,3,4,5,6,7,8,9,1,2,3,4,5,6,7,8,9,1,2,3,4,5,6,7,8,9};
    pc.setBinary(b);
    return pc;
  }
  



}





