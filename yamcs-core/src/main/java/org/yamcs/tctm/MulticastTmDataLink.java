package org.yamcs.tctm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.archive.PacketWithTime;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

import org.yamcs.utils.CcsdsPacket;
import org.yamcs.utils.TimeEncoding;


/**
 * Receives telemetry packets via multicast from TMR. The nice thing about multicast is that there is no 
 * 	connection required so the code can be fairly simple.
 * Keeps simple statistics about the number of datagram received and the number of too short datagrams
 * @author nm
 *
 */
public class MulticastTmDataLink extends AbstractExecutionThreadService implements TmPacketDataLink {
    private volatile int validDatagramCount = 0;
    private volatile int invalidDatagramCount = 0;
    private volatile boolean disabled=false;

    private volatile boolean quitting=false;
    private MulticastSocket tmSocket;
    private String group="239.192.0.1";
    private int port=31002;

    private TmSink tmSink;

    private Logger log=LoggerFactory.getLogger(this.getClass().getName());
    final int maxLength=1500; //maximum length of tm packets in columbus is 1472
    DatagramPacket datagram = new DatagramPacket(new byte[maxLength], maxLength);

    /**
     * Creates a 
     * @param spec
     * @throws ConfigurationException if tmGroup or tmPort are not defined in the configuration files 
     */
    public MulticastTmDataLink(String instance, String name, String spec) throws ConfigurationException  {
        YConfiguration c=YConfiguration.getConfiguration("multicast");
        group=c.getString(spec, "tmGroup");
        port=c.getInt(spec, "tmPort");
        try {
            openSocket();
        } catch (IOException e) {
            throw new ConfigurationException("IOException caught when opening the multicast socket: "+e);
        }
    }

    /**
     * Creates a simple multicast receiver listening to the given group and port
     * @param group
     * @param port
     * @throws IOException if there was an exception opening the port (happends when there is alredy another process bound 
     * to that port and the option REUSE_ADDR is not set on its socket)
     */
    public MulticastTmDataLink(String group, int port) throws IOException {
        this.group=group;
        this.port=port;
        openSocket();
    }


    private void openSocket() throws IOException {
        tmSocket=new MulticastSocket(port);
        tmSocket.joinGroup(InetAddress.getByName(group));
    }

    @Override
    public void setTmSink(TmSink tmSink) {
        this.tmSink=tmSink;
    }

    @Override
    public void run() {
        while(!quitting) {
            PacketWithTime pwrt=getNextPacket();
            tmSink.processPacket(pwrt);
        }
    }
    /**
     * 
     * Called to retrieve the next packet.
     * It blocks in readining on the multicast socket  
     * @return anything that looks as a valid packet, just the size is taken into account to decide if it's valid or not
     */
    public PacketWithTime getNextPacket() {
        ByteBuffer packet = null;
        while(disabled) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return null;
            }
        }
        long rectime=TimeEncoding.INVALID_INSTANT;
        while (isRunning()) {
            try {
                tmSocket.receive(datagram);
                /*the packet received from TMR has a 10 bytes header followed by the CCSDS packet:
                 *  some kind of identification always 0x0B for TM and 06 for PP - 1 byte - the list with all the packet types can be found in the DaSS_C_Packet_Decoder.java part of the DaSS API
                 *  ccsds time                                                 - 5 bytes
                 *  requestId (always set to 0 (I guess only for multicast)    - 4 bytes
                 *   ccsds packet
                 *  
                 *  The time in the "TMR header" is the reception time. It looks like the CCSDS GPS but is generated locally so it's UNIX time in fact
                 */
                if(datagram.getLength()<26) { //10 for the TMR header plus 6 for the primary CCSDS header plus 10 for secondary CCSDS header
                    log.warn("Incomplete packet received on the multicast, discarded: {}", datagram);
                    continue;
                }

                byte[] data = datagram.getData();                
                int offset = datagram.getOffset();
                
                ByteBuffer  bb = ByteBuffer.wrap(data);

                //the time sent by TMR is not really GPS, it's the unix local computer time shifted to GPS epoch
                long unixTimesec=(0xFFFFFFFFL & (long)bb.getInt(offset+1))+315964800L;
                int unixTimeMicrosec=(0xFF&bb.get(offset+5))*(1000000/256);
                rectime = TimeEncoding.fromUnixTime(unixTimesec, unixTimeMicrosec);
                int pktLength = 7+((data[14+offset]&0xFF)<<8)+(data[15+offset]&0xFF);
                if(pktLength<16) {
                    invalidDatagramCount++;
                    log.warn("Invalid packet received on the multicast, pktLength: {}. Expecting minimum 16 bytes", pktLength);
                    continue;
                }
                if(datagram.getLength()<10+pktLength) {
                    invalidDatagramCount++;
                    log.warn("Incomplete packet received on the multicast. expected {}, received: {}", pktLength, (datagram.getLength()-10));
                    continue;
                }
                validDatagramCount++;
                packet = ByteBuffer.allocate(pktLength);
                packet.put(data, offset+10, pktLength);
                break;
            } catch (IOException e) {
                log.warn("exception {} thrown when reading from the multicast socket {}:{}", group, port, e);
            }
        }
        
        if(packet!=null) {
            return new PacketWithTime(rectime, CcsdsPacket.getInstant(packet), packet.array());
        } else {
            return null;
        }

    }

    @Override
    public String getLinkStatus() {
        return disabled?"DISABLED":"OK";
    }

    /**
     * returns statistics with the number of datagram received and the number of invalid datagrams
     */
    @Override
    public String getDetailedStatus() {
        if(disabled) {
            return "DISABLED";
        } else {
            return String.format("OK (%s:%d)%nValid datagrams received: %d%nInvalid datagrams received: %d",
                    group, port, validDatagramCount, invalidDatagramCount);
        }
    }

    /**
     * Sets the disabled to true such that getNextPacket ignores the received datagrams
     */
    @Override
    public void disable() {
        disabled=true;
    }

    /**
     * Sets the disabled to false such that getNextPacket does not ignore the received datagrams
     */
    @Override
    public void enable() {
        disabled=false;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public long getDataCount() {
        return validDatagramCount;
    }
}
