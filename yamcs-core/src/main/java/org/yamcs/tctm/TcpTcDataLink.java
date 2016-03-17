package org.yamcs.tctm;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.parameter.SystemParametersCollector;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;

import com.google.common.util.concurrent.AbstractService;

/**
 * Sends raw packets on Tcp socket.
 * @author nm
 *
 */
public class TcpTcDataLink extends AbstractService implements Runnable, TcDataLink,  SystemParametersProducer {
    protected SocketChannel socketChannel=null;
    protected String host="whirl";
    protected int port=10003;
    protected CommandHistoryPublisher commandHistoryListener;
    protected Selector selector; 
    SelectionKey selectionKey;
    protected CcsdsSeqAndChecksumFiller seqAndChecksumFiller=new CcsdsSeqAndChecksumFiller();
    protected ScheduledThreadPoolExecutor timer;
    protected volatile boolean disabled=false;
    protected int minimumTcPacketLength = -1; //the minimum size of the CCSDS packets uplinked
    volatile long tcCount;
    private NamedObjectId sv_linkStatus_id, sp_dataCount_id;

    private SystemParametersCollector sysParamCollector;
    protected final Logger log;
    private String yamcsInstance;
    private String name;
    TimeService timeService;
    
    public TcpTcDataLink(String yamcsInstance, String name, String spec) throws ConfigurationException {
        log=YamcsServer.getLogger(this.getClass(), yamcsInstance);
        YConfiguration c = YConfiguration.getConfiguration("tcp");
        this.yamcsInstance=yamcsInstance;
        host=c.getString(spec, "tcHost");
        port=c.getInt(spec, "tcPort");
        this.name = name;
        if(c.containsKey(spec, "minimumTcPacketLength")) {
            minimumTcPacketLength = c.getInt(spec, "minimumTcPacketLength");
        } else {
            log.debug("minimumTcPacketLength not defined, using the default value "+minimumTcPacketLength);
        }
        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    protected TcpTcDataLink() {
        log=LoggerFactory.getLogger(this.getClass().getName());
    } // dummy constructor which is automatically invoked by subclass constructors

    public TcpTcDataLink(String host, int port) {
        this.host=host;
        this.port=port;
        openSocket();
        log=LoggerFactory.getLogger(this.getClass().getName());
    }

    protected long getCurrentTime() {
        if(timeService!=null) {
            return timeService.getMissionTime();
        } else {
            return TimeEncoding.fromUnixTime(System.currentTimeMillis());
        }
    }
    @Override
    protected void doStart() {
        setupSysVariables();
        this.timer=new ScheduledThreadPoolExecutor(1);
        timer.scheduleWithFixedDelay(this, 0, 10, TimeUnit.SECONDS);
        notifyStarted();
    }

    protected void openSocket() {
        try {
            InetAddress address=InetAddress.getByName(host);
            socketChannel=SocketChannel.open(new InetSocketAddress(address,port));
            socketChannel.configureBlocking(false);
            socketChannel.socket().setKeepAlive(true);
            selector = Selector.open();
            selectionKey=socketChannel.register(selector,SelectionKey.OP_WRITE|SelectionKey.OP_READ);
            log.info("TC connection established to "+host+":"+port);
        } catch (IOException e) {
            String exc = (e instanceof ConnectException) ? ((ConnectException) e).getMessage() : e.toString();
            log.info("Cannot open TC connection to "+host+":"+port+" '"+exc+"'. Retrying in 10s");
            try {socketChannel.close();} catch (Exception e1) {}
            try {selector.close();} catch (Exception e1) {}
            socketChannel=null;
        }
    }

    protected void disconnect() {
        if(socketChannel==null) return;
        try {
            socketChannel.close();
            selector.close();
            socketChannel=null;
        } catch (IOException e) {
            log.warn("Exception caught when checking if the socket to "+host+":"+port+" is open", e);
        }
    }
    /**
     * we check if the socket is open by trying a select on the read part of it
     * @return
     */
    protected boolean isSocketOpen() {
        final ByteBuffer bb=ByteBuffer.allocate(16);
        if(socketChannel==null) {
            return false;
        }

        boolean connected=false;
        try {
            selector.select();
            if(selectionKey.isReadable()) {
                int read=socketChannel.read(bb);
                if(read>0) {
                    log.info("Data read on the TC socket to "+host+":"+port+"!! :"+bb);
                    connected=true;
                } else if(read<0) {
                    log.warn("TC socket to "+host+":"+port+" has been closed");
                    socketChannel.close();
                    selector.close();
                    socketChannel=null;
                    connected=false;
                }
            } else if(selectionKey.isWritable()){
                connected=true;
            } else {
                log.warn("The TC socket to "+host+":"+port+" is neither writable nor readable");
                connected=false;
            }
        } catch (IOException e) {
            log.warn("Exception caught when checking if the socket to "+host+":"+port+" is open:", e);
            connected=false;
        }
        return connected;
    }

    /**
     * Sends 
     */
    @Override
    public void sendTc(PreparedCommand pc) {
        if(disabled) {
            log.warn("TC disabled, ignoring command "+pc.getCommandId());
            return;
        }
        ByteBuffer bb=null;
        if(pc.getBinary().length<minimumTcPacketLength) { //enforce the minimum packet length
            bb=ByteBuffer.allocate(minimumTcPacketLength);
            bb.put(pc.getBinary());
            bb.putShort(4, (short)(minimumTcPacketLength - 7)); // fix packet length
        } else {
            bb=ByteBuffer.wrap(pc.getBinary());
        }

        int retries=5;
        boolean sent=false;
        int seqCount=seqAndChecksumFiller.fill(bb, pc.getCommandId().getGenerationTime());
        bb.rewind();
        while (!sent&&(retries>0)) {
            if (!isSocketOpen()) {
                openSocket();
            }

            if(isSocketOpen()) {
                try {
                    socketChannel.write(bb);
                    tcCount++;
                    sent=true;
                } catch (IOException e) {
                    log.warn("Error writing to TC socket to "+host+":"+port+": "+e.getMessage());
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

        if(sent) {
            handleAcks(pc.getCommandId(), seqCount);
        } else {
            timer.schedule(new TcAckStatus(pc.getCommandId(), "Acknowledge_FSC_Status","NACK"), 100, TimeUnit.MILLISECONDS);
        }
    }

    protected void handleAcks(CommandId cmdId, int seqCount ) {
        timer.schedule(new TcAck(cmdId,"Final_Sequence_Count", Integer.toString(seqCount)), 200, TimeUnit.MILLISECONDS);
        timer.schedule(new TcAckStatus(cmdId,"Acknowledge_FSC","ACK: OK"), 400, TimeUnit.MILLISECONDS);
        timer.schedule(new TcAckStatus(cmdId,"Acknowledge_FRC","ACK: OK"), 800, TimeUnit.MILLISECONDS);
        timer.schedule(new TcAckStatus(cmdId,"Acknowledge_DASS","ACK: OK"), 1200, TimeUnit.MILLISECONDS);
        timer.schedule(new TcAckStatus(cmdId,"Acknowledge_MCS","ACK: OK"), 1600, TimeUnit.MILLISECONDS);
        timer.schedule(new TcAckStatus(cmdId,"Acknowledge_A","ACK A: OK"), 2000, TimeUnit.MILLISECONDS);
        timer.schedule(new TcAckStatus(cmdId,"Acknowledge_B","ACK B: OK"), 3000, TimeUnit.MILLISECONDS);
        timer.schedule(new TcAckStatus(cmdId,"Acknowledge_C","ACK C: OK"), 4000, TimeUnit.MILLISECONDS);
        timer.schedule(new TcAckStatus(cmdId,"Acknowledge_D","ACK D: OK"), 10000, TimeUnit.MILLISECONDS);
    }

    @Override
    public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
        this.commandHistoryListener=commandHistoryListener;
    }

    @Override
    public String getLinkStatus() {
        if (disabled) return "DISABLED";
        if(isSocketOpen()) {
            return "OK";
        } else {
            return "UNAVAIL";
        }
    }

    @Override
    public String getDetailedStatus() {
        if(disabled) 
            return String.format("DISABLED (should connect to %s:%d)", host, port);
        if(isSocketOpen()) {
            return String.format("OK, connected to %s:%d", host, port);
        } else {
            return String.format("Not connected to %s:%d", host, port);
        }
    }

    @Override
    public void disable() {
        disabled=true;
        if(isRunning()) {
            disconnect();
        }
    }

    @Override
    public void enable() {
        disabled=false;
    }		

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public void run() {
        if(!isRunning() || disabled) return;
        if (!isSocketOpen()) {
            openSocket();
        }
    }

    @Override
    public void doStop() {
        disconnect();
        notifyStopped();
    }

    public static void main(String[] argv) throws ConfigurationException, InterruptedException {
        TcpTcDataLink tc=new TcpTcDataLink("epss", "test", "epss");
        PreparedCommand pc=new PreparedCommand(new byte[20]);
        for(int i=0;i<10;i++) {
            System.out.println("getFwLinkStatus: "+tc.getLinkStatus());
            Thread.sleep(3000);
        }
        tc.sendTc(pc);
    }

    class TcAck implements Runnable {
        CommandId cmdId;
        String name;
        String value;
        TcAck(CommandId cmdId, String name, String value) {
            this.cmdId=cmdId;
            this.name=name;
            this.value=value;
        }
        @Override
        public void run() {
            commandHistoryListener.updateStringKey(cmdId,name,value);
        }       
    }

    class TcAckStatus extends TcAck {
        TcAckStatus(CommandId cmdId, String name, String value) {
            super(cmdId, name, value);
        }
        @Override
        public void run() {
            long instant = getCurrentTime();
            commandHistoryListener.updateStringKey(cmdId,name+"_Status",value);
            commandHistoryListener.updateTimeKey(cmdId,name+"_Time", instant);
        }		
    }


    @Override
    public long getDataCount() {
        return tcCount;
    }


    protected void setupSysVariables() {
        this.sysParamCollector = SystemParametersCollector.getInstance(yamcsInstance);
        if(sysParamCollector!=null) {
            sysParamCollector.registerProvider(this, null);
            sv_linkStatus_id = NamedObjectId.newBuilder().setName(sysParamCollector.getNamespace()+"/"+name+"/linkStatus").build();
            sp_dataCount_id = NamedObjectId.newBuilder().setName(sysParamCollector.getNamespace()+"/"+name+"/dataCount").build();


        } else {
            log.info("System variables collector not defined for instance {} ", yamcsInstance);
        }

    }
    @Override
    public Collection<ParameterValue> getSystemParameters() {
        long time = getCurrentTime();
        ParameterValue linkStatus = SystemParametersCollector.getPV(sv_linkStatus_id, time, getLinkStatus());
        ParameterValue dataCount = SystemParametersCollector.getPV(sp_dataCount_id, time, getDataCount());
        return Arrays.asList(linkStatus, dataCount);
    }

    public int getMiniminimumTcPacketLength() {
        return minimumTcPacketLength;
    }
}

