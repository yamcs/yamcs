package org.yamcs.tctm;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.parameter.SystemParametersCollector;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.time.TimeService;
import org.yamcs.utils.CcsdsPacket;

import com.google.common.util.concurrent.AbstractExecutionThreadService;


public class TcpTmDataLink extends AbstractExecutionThreadService implements TmPacketDataLink,  SystemParametersProducer {
    protected volatile long packetcount = 0;
    protected Socket tmSocket;
    protected String host="localhost";
    protected int port=10031;
    protected volatile boolean disabled=false;

    protected final Logger log;
    private TmSink tmSink;
    

    private SystemParametersCollector sysParamCollector;
    ParameterValue svConnectionStatus;
    List<ParameterValue> sysVariables= new ArrayList<ParameterValue>();
    private NamedObjectId sv_linkStatus_id, sp_dataCount_id;
    final String yamcsInstance;
    final String name;
    final TimeService timeService;
    
    protected TcpTmDataLink(String instance, String name) {// dummy constructor needed by subclass constructors
        this.yamcsInstance = instance;
        this.name = name;
        this.timeService = YamcsServer.getTimeService(instance);
        log = YamcsServer.getLogger(this.getClass(), instance);
    }

    public TcpTmDataLink(String instance, String name, String spec) throws ConfigurationException  {
        this(instance, name);
        
        YConfiguration c=YConfiguration.getConfiguration("tcp");
        host=c.getString(spec, "tmHost");
        port=c.getInt(spec, "tmPort");
    }

    protected void openSocket() throws IOException {
        InetAddress address=InetAddress.getByName(host);
        tmSocket=new Socket();
        tmSocket.setKeepAlive(true);
        tmSocket.connect(new InetSocketAddress(address,port),1000);
    }

    @Override
    public void setTmSink(TmSink tmSink) {
        this.tmSink=tmSink;
    }

    @Override
    public void run() {
        setupSysVariables();
        while(isRunning()) {
            PacketWithTime pwrt=getNextPacket();
            if(pwrt==null) break;
            tmSink.processPacket(pwrt);
        }
    }

    public PacketWithTime getNextPacket() {
        ByteBuffer bb=null;
        while (isRunning()) {
            while(disabled) {
                if(!isRunning()) return null;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            try {
                if (tmSocket==null) {
                    openSocket();
                    log.info("TM connection established to "+host+":"+port);
                } 
                byte hdr[] = new byte[6];
                if(!readWithBlocking(hdr,0,6))
                    continue;
                int remaining=((hdr[4]&0xFF)<<8)+(hdr[5]&0xFF)+1;
                bb=ByteBuffer.allocate(6+remaining).put(hdr);
                if(!readWithBlocking(bb.array(), 6, remaining)) 
                    continue;
                bb.rewind();
                packetcount++;
                break;
            } catch (IOException e) {
                String exc = (e instanceof ConnectException) ? ((ConnectException) e).getMessage() : e.toString();
                log.info("Cannot open or read TM socket "+host+":"+port+" '"+exc+"'. Retrying in 10s");
                try {tmSocket.close();} catch (Exception e2) {}
                tmSocket=null;
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e1) {
                    log.warn("Exception "+ e1.toString()+" while sleeping for 10s");
                    return null;
                }
            }
        }
        if(bb!=null) {
            return new PacketWithTime(timeService.getMissionTime(), CcsdsPacket.getInstant(bb), bb.array());
        } 
        return null;
    }
    
    @Override
    public boolean isArchiveReplay() {
        return false;
    }
    
    /**
     * Read n bytes from the tmSocket, blocking if necessary till all bytes are available.
     * Returns true if all the bytes have been read and false if the stream has closed before all the bytes have been read.
     * @param b
     * @param n
     * @return
     * @throws IOException 
     */
    protected boolean readWithBlocking(byte[] b, int pos, int n) throws IOException {
        InputStream in=tmSocket.getInputStream();
        int remaining=n;
        while(remaining>0) {
            int read=in.read(b,pos,remaining);
            if(read==-1) {
                log.warn("Tm Connection closed");
                if(remaining!=n) {
                    log.warn("Discarding incomplete TM packet read: expected "+n+", read"+(n-remaining)+". Packet discarded.");
                }
                tmSocket=null;
                return false;
            }
            remaining-=read;
            pos+=read;
        }
        return true;
    }

    @Override
    public String getLinkStatus() {
        if (disabled) return "DISABLED";
        if (tmSocket==null) {
            return "UNAVAIL";
        } else {
            return "OK";
        }
    }

    @Override
    public void triggerShutdown() {
        if(tmSocket!=null) {
            try {
                tmSocket.close();
            } catch (IOException e) {
                log.warn("Exception got when closing the tm socket:", e);
            }
            tmSocket=null;
        }
    }

    @Override
    public void disable() {
        disabled=true;
        if(tmSocket!=null) {
            try {
                tmSocket.close();
            } catch (IOException e) {
                log.warn("Exception got when closing the tm socket:", e);
            }
            tmSocket=null;
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
    public String getDetailedStatus() {
        if(disabled) {
            return String.format("DISABLED (should connect to %s:%d)", host, port);
        }
        if (tmSocket==null) {
            return String.format("Not connected to %s:%d", host, port);
        } else {
            return String.format("OK, connected to %s:%d, received %d packets", host, port, packetcount);
        }
    }

    @Override
    public long getDataCount() {
        return packetcount;
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
        long time = timeService.getMissionTime();
        ParameterValue linkStatus = SystemParametersCollector.getPV(sv_linkStatus_id, time, getLinkStatus());
        ParameterValue dataCount = SystemParametersCollector.getPV(sp_dataCount_id, time, getDataCount());
        return Arrays.asList(linkStatus, dataCount);
    }
}
