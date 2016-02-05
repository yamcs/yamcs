package org.yamcs.ui;

import static org.yamcs.api.Protocol.DATA_TYPE_HEADER_NAME;
import static org.yamcs.api.Protocol.decode;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.Semaphore;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.yamcs.YamcsException;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnectData;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.utils.CcsdsPacket;
import org.yamcs.utils.PacketFormatter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.MdbMappings;

/**
 * Command line tool to retrieve packets
 * @author nm
 *
 */
public class CliPacketDump {
    private static void printUsageAndExit(String error) {
    	if(error!=null) System.err.println(error);
        System.err.println("Usage: packet-dump.sh OPTIONS yamcs-url start stop packetnames");
        System.err.println(" -x   print the packet in hexadecimal rather than raw. Each packet is followed by an empty line.");
        System.err.println(" -wc  print packets without CCSDS header.");
        System.err.println(" -p   print packets with a fake 32 bytes pacts header and without CCSDS header.");
        System.err.println(" -oh  print only the header of the packets, not the body");
        System.err.println("\nExample:\n packet-dump.sh yamcs://localhost/yops 2007-06-02T18:32:01 2007-12-31T23:59:59 \"EUTEF_INST_EUTEMP_HK_EUTEMP EUTEF_Tlm_Pkt_HK_DHPU\"");
        System.exit(-1);
    }
    
    /**
     * @param args
     * yamcs://aces-test/aces-test 2010-05-19T00:00:00 2011-05-20T00:00:00   aces_SHM_HP_SET aces_SHM_HP_MEAS aces_SHM_IS_HEATER
     * 
     */
    public static void main(String[] args) throws YamcsApiException, URISyntaxException, HornetQException, IOException, YamcsException, InterruptedException {

        final PacketFormatter packetFormatter=new PacketFormatter(System.out);
        int argcnt=0;
        for(argcnt=0;argcnt<args.length;argcnt++) {
            if(args[argcnt].equals("-x")) {
                packetFormatter.setHex(true);
                continue;
            }
            if(args[argcnt].equals("-wc")) {
                packetFormatter.setWithoutCcsds(true);
                continue;
            }
            if(args[argcnt].equals("-oh")) {
                packetFormatter.setOnlyHeader(true);
                continue;
            }
            if(args[argcnt].equals("-p")) {
                packetFormatter.setWithPacts(true);
                packetFormatter.setWithoutCcsds(true);
                continue;
            }
            if(!args[argcnt].startsWith("-")) {
                break;
            }
        }
        if((args.length-argcnt)<3) printUsageAndExit("too few arguments");

        YamcsConnectData ycd=YamcsConnectData.parse(args[argcnt++]);
        if(ycd.getInstance()==null) printUsageAndExit("The Yamcs URL does not contain the archive instance. Use something like yamcs://hostname/archiveInstance");

        TimeEncoding.setUp();
        
        long start,stop;
        start = TimeEncoding.parse(args[argcnt++]);
        stop = TimeEncoding.parse(args[argcnt++]);

        ReplayRequest.Builder rrb=ReplayRequest.newBuilder();
        
        PacketReplayRequest.Builder prrb=PacketReplayRequest.newBuilder();
        while(argcnt<args.length) {
            prrb.addNameFilter(NamedObjectId.newBuilder().setName(args[argcnt++]).setNamespace(MdbMappings.MDB_OPSNAME));
        }
        
        rrb.setPacketRequest(prrb).setEndAction(EndAction.STOP).setStart(start).setStop(stop);
        
        YamcsSession ysession=YamcsSession.newBuilder().setConnectionParams(ycd).build();
        YamcsClient yclient=ysession.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
        
        StringMessage answer = (StringMessage) yclient.executeRpc(Protocol.getYarchRetrievalControlAddress(ycd.getInstance()),
                        "createReplay", rrb.build(), StringMessage.newBuilder());
        SimpleString packetReplayAddress=new SimpleString(answer.getMessage());
            

        final Semaphore semaphore=new Semaphore(0);
        yclient.dataConsumer.setMessageHandler(new MessageHandler() {
            @Override
            public void onMessage(ClientMessage pmsg) {
                try {
                    int t=pmsg.getIntProperty(DATA_TYPE_HEADER_NAME);
                    ProtoDataType pdt=ProtoDataType.valueOf(t);
                    if(pdt==ProtoDataType.STATE_CHANGE) {
                    	packetFormatter.close();
                        semaphore.release();
                    } else {
                    	TmPacketData pdata=(TmPacketData)decode(pmsg, TmPacketData.newBuilder());
                    	packetFormatter.writePacket(new CcsdsPacket(pdata.getPacket().asReadOnlyByteBuffer()));
                    }
                } catch (Exception e) {
                    System.err.println("cannot decode packet message"+e);
                }
            }
        });
        yclient.executeRpc(packetReplayAddress, "start", null, null);
        semaphore.acquire();
        yclient.close();
        ysession.close();
    }
}
