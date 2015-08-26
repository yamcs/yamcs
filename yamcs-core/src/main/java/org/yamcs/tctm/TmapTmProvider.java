package org.yamcs.tctm;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.archive.PacketWithTime;

import org.yamcs.utils.CcsdsPacket;
import org.yamcs.utils.TimeEncoding;


/**
 * Provides packets directly from the tmap process (part of pacts)
 * 
 */
public class TmapTmProvider extends TcpTmProvider {
	public TmapTmProvider(String instance, String name, String spec) throws ConfigurationException {
	    super(instance, name);
		YConfiguration c=YConfiguration.getConfiguration("tmaptcap");
		host=c.getString(spec, "tmHost");
		port=c.getInt(spec, "tmPort");
	}

	@SuppressWarnings("null")
    @Override
    public PacketWithTime getNextPacket() {
		while(disabled) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				return null;
			}
		}
		ByteBuffer bb=null;
		byte tmaphdr[] = new byte[32];
		byte hdr[] = new byte[6];
		while (isRunning()) {
			try {
				if (tmSocket==null) {
					openSocket();
					log.info("Tmap connection established to "+host+" port "+port);
				} 
				if(!readWithBlocking(tmaphdr,0,32)) continue;
				if(!readWithBlocking(hdr,0,6)) continue;
				int remaining=((hdr[4]&0xFF)<<8)+(hdr[5]&0xFF)+1;
				bb=ByteBuffer.allocate(6+remaining).put(hdr);
				if(!readWithBlocking(bb.array(), 6, remaining)) 
					continue;
				bb.rewind();
				packetcount++;
				break;
			} catch (IOException e) {
				log.info("Cannot open or read from Tmap socket at "+host+":"+port+": "+e+"; retrying in 10 seconds.");
				try {tmSocket.close();} catch (Exception e2) {}
				tmSocket=null;
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e1) {
					log.warn("exception "+ e.toString()+" thrown when sleeping 10 sec");
				}
			}
		}
		return new PacketWithTime(timeService.getMissionTime(), CcsdsPacket.getInstant(bb), bb.array());
	}
}
