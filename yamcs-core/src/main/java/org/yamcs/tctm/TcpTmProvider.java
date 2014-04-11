package org.yamcs.tctm;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.TmProcessor;
import org.yamcs.YConfiguration;
import org.yamcs.archive.PacketWithTime;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

import org.yamcs.utils.CcsdsPacket;
import org.yamcs.utils.TimeEncoding;


public class TcpTmProvider extends AbstractExecutionThreadService implements TmPacketProvider {
	protected volatile long packetcount = 0;
	protected Socket tmSocket;
	protected String host="localhost";
	protected int port=10031;
	protected volatile boolean disabled=false;

	protected Logger log=LoggerFactory.getLogger(this.getClass().getName());
    private TmProcessor tmProcessor;

	protected TcpTmProvider() {} // dummy constructor which is automatically invoked by subclass constructors

	public TcpTmProvider(String instance, String name, String spec) throws ConfigurationException  {
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
	public void setTmProcessor(TmProcessor tmProcessor) {
	    this.tmProcessor=tmProcessor;
	}
	
	@Override
	public void run() {
	    while(isRunning()) {
	        PacketWithTime pwrt=getNextPacket();
	        if(pwrt==null) break;
	        tmProcessor.processPacket(pwrt);
	    }
	    tmProcessor.finished();
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
					log.info("TM connection estabilished to "+host+" port "+port);
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
				log.info("Cannot open or read from TM socket at "+host+":"+port+": "+e+"; retrying in 10 seconds.");
				try {tmSocket.close();} catch (Exception e2) {}
				tmSocket=null;
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e1) {
					log.warn("exception "+ e1.toString()+" thrown when sleeping 10 sec");
					return null;
				}
			}
		}
		if(bb!=null) {
		    return new PacketWithTime(TimeEncoding.currentInstant(), CcsdsPacket.getInstant(bb), bb.array());
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
}

