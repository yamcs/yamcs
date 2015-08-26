package org.yamcs.tctm;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;

import org.yamcs.utils.CcsdsPacket;

/*

C-structure of Tmap PaCTS protocol "32-byte" header:
(see pacts/DATALINKS/Tcp/HDR/pactsmessage.h)

typedef struct tagTransport {
   long   size;        // Size of the message to be tranported
   int    APID;        // APID for this message
   int    PktID;       // PktID in this message
   int    TmTime;      // Time stamp
   int    TmFine;      //   of this message
} TTransport;

typedef struct tagProcess {
   long   sessionId;             // Session Id 
   long   sequenceId;            // Sequence Id
   long   operationId;           // Operation Id
} TProcess;

typedef struct tagPactsMsgHeader {
   TTransport transportInfo;      // transport layer
   TProcess processInfo;          // process layer
} TPactsMsgHeader;

 */

public class TcapTcUplinker extends TcpTcUplinker {
    boolean addCgsHeader; //add or not the CarloGavazi header required on the EuTEF EGSE and on one of the SOLAR EGSEs 

    public TcapTcUplinker(String spec) throws ConfigurationException  {
        YConfiguration c=YConfiguration.getConfiguration("tmaptcap");
        host=c.getString(spec, "tcHost");
        port=c.getInt(spec, "tcPort");
        if(c.containsKey(spec, "addCgsHeader")) {
            addCgsHeader=c.getBoolean(spec, "addCgsHeader");
        } else {
            addCgsHeader=false;
        }
        this.timer=new  ScheduledThreadPoolExecutor(1);
        openSocket();
        timer.scheduleWithFixedDelay(this, 0, 10, TimeUnit.SECONDS);
    }

    @Override
    public void sendTc(PreparedCommand pc) {
        if(disabled) {
            log.warn("TC disabled, ignoring command "+pc.getCommandId());
            return;
        }
        ByteBuffer cgsBb=null;
        if(addCgsHeader) {
            String cgsHeader="TC: CDMCS_COMMAND";
            cgsBb=ByteBuffer.allocate(cgsHeader.length()+1);
            cgsBb.put(cgsHeader.getBytes());
            cgsBb.rewind();
        }

        ByteBuffer pactsMsgHdr = ByteBuffer.allocate(32);
        ByteBuffer bb=ByteBuffer.wrap(pc.getBinary());
        CcsdsPacket ccsds = new CcsdsPacket(bb);
        if ( ccsds.getChecksumIndicator() ) {
            bb.limit(bb.limit() - 2); // remove checksum if present
        }
        pactsMsgHdr.putInt(bb.limit() - 16 + ((cgsBb!=null)?cgsBb.limit():0)); // without CCSDS header
        pactsMsgHdr.putInt(ccsds.getAPID() | 0x1000); // this is a PAYLOAD packet (not a SYSTEM packet)
        pactsMsgHdr.putInt(ccsds.getPacketID());
        pactsMsgHdr.putInt((int)ccsds.getCoarseTime());
        pactsMsgHdr.putInt(ccsds.getFineTime());
        pactsMsgHdr.rewind();

        int retries=5;
        boolean sent=false;
        //int seqCount=seqAndChecksumFiller.fill(bb);
        //bb.rewind();
        bb.position(16); // skip CCSDS header
        while (!sent&&(retries>0)) {
            if (!isSocketOpen()) {
                openSocket();
            }

            if(isSocketOpen()) {
                try {
                    socketChannel.write(pactsMsgHdr);
                    if(addCgsHeader) socketChannel.write(cgsBb);
                    socketChannel.write(bb);
                    tcCount++;
                    sent=true;
                } catch (IOException e) {
                    log.warn("Error writing to TC socket to "+host+":"+port+": ", e);
                    try {
                        if(socketChannel.isOpen()) socketChannel.close();
                        selector.close();
                        socketChannel=null;
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
            retries--;
            if(!sent && (retries>0)) {
                try {
                    log.warn("Command not sent, retrying in 2 seconds");
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    log.warn("exception "+ e.toString()+" thrown when sleeping 2 sec");
                }
            }
        }
        if(commandHistoryListener!=null) {
            if(sent) {
                handleAcks(pc.getCommandId(), 0); //unfortunately we have no clue what sequence count tcap will add
            } else {
                timer.schedule(new TcAckStatus(pc.getCommandId(), "Acknowledge_FSC_Status","NACK"), 100, TimeUnit.MILLISECONDS);
            }
        }
    }
}
