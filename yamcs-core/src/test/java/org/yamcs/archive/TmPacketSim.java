package org.yamcs.archive;

import java.io.IOException;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.tctm.TmFileReader;

import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.utils.TimeEncoding;

/**
 * 
 * Sends some events to yarch
 *
 */
public class TmPacketSim {
    YamcsClient msgClient;
    volatile boolean quitting;
    final SimpleString address;
    TmPacketSim(String instance, String host, int port) throws HornetQException, YamcsApiException {
        YamcsSession ys=YamcsSession.newBuilder().setConnectionParams(host, port).build();
        msgClient=ys.newClientBuilder().setRpc(false).setDataProducer(true).build();
        address=new SimpleString(instance+".tm.realtime");
    }
    
    
    public void sendPackets() throws HornetQException, IOException, InterruptedException {
        TmFileReader ftp=new TmFileReader("/home/mu/24/03");
       // ftp.setDelayBetweenPackets(delayBetweenPackets);
        int i=0;
        PacketWithTime pwrt;
        while((pwrt=ftp.readPacket())!=null) {
            Thread.sleep(5000);
//            TmPacketData tpd=TmPacketData.newBuilder().setReceptionTime(pwrt.rectime).
//                setPacket(ByteString.copyFrom(pwrt.bb)).build();
//            msgClient.sendData(address, ProtoDataType.TM_PACKET, tpd);
        }
    }

    public void stop() {
        quitting=true;
    }
    
    static void printUsageAndExit() {
        System.err.println("Usage: tmpacket-sim.sh [-h host -p port] instance");
        System.exit(-1);
    }
    /**
     * @param args
     * @throws HornetQException 
     * @throws IOException 
     */
    public static void main(String[] args) throws Exception {
    
        int i=0;
        String host="localhost";
        int port=5445;
        String instance=null;
        while(i<args.length) {
            if("-h".equals(args[i])) {
                host=args[++i];
            } else if("-p".equals(args[i])) {
                port=Integer.parseInt(args[++i]);
            } else {
                instance=args[i];
            }
            i++;
        }
        if(instance==null) printUsageAndExit();
        YConfiguration.setup();
        YamcsServer.setupHornet();
        
        TimeEncoding.setUp();
      //  Configuration.setup();
        new TmPacketSim(instance, host, port).sendPackets();
    }
}
