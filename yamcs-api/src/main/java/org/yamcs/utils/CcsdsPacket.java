package org.yamcs.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.yamcs.utils.TimeEncoding;



public class CcsdsPacket implements Comparable<CcsdsPacket>{
    static public final int DATA_OFFSET=16;
    
	static public final int MAX_CCSDS_SIZE=1500;
	protected ByteBuffer bb;
	
	public CcsdsPacket( byte[] packet) {
		bb = ByteBuffer.wrap(packet);
	}

	public CcsdsPacket(ByteBuffer bb) {
		this.bb=bb;
	}

	public int getSecondaryHeaderFlag() {
		return (bb.getShort(0)>>11)&1;
	}

	public int getSequenceCount() {
		return bb.getShort(2)&0x3FFF;
	}
	
	public void setSequenceCount(short seqCount) {
	   short oldSeqField = bb.getShort(2);
	   short seqInd = (short)(oldSeqField&(~0x3FFF));
	   bb.putShort(2, (short)((seqCount&0x3FFF) + seqInd));
	}

	public static short getSequenceCount(ByteBuffer bb) {
		return (short) (bb.getShort(2)&0x3FFF);
	}

	public int getAPID() {
		return bb.getShort(0)& 0x07FF;
	}
	
	public void setAPID(int apid) {
		int tmp=bb.getShort(0) & (~0x07FF);
		tmp=tmp|apid;
		bb.putShort(0,(short)tmp);
	}	
	public static short getAPID(ByteBuffer bb) {
		return (short)(bb.getShort(0)& 0x07FF);
	}
	
	/*returns the length written in the ccsds header*/
    public static int getCccsdsPacketLength(ByteBuffer bb) {
        return bb.getShort(4)&0xFFFF;
    }
    
	
	/*returns the length written in the ccsds header*/
	public int getCccsdsPacketLength() {
		return getCccsdsPacketLength(bb);
	}
	
    public void setCccsdsPacketLength(short length) {
        //return bb.getShort(4)&0xFFFF;
        bb.putShort(4, length);
    }
	
	/**returns the length of the packet, normally equals ccsdslength+7*/
	public int getLength() {
		return bb.capacity();
	}
	
	/**
	 * 
	 * @return instant
	 */
	public long getInstant() {
		return TimeEncoding.fromGpsCcsdsTime(bb.getInt(6), bb.get(10)) ;
	}

	public static long getInstant(ByteBuffer bb) {
		return TimeEncoding.fromGpsCcsdsTime(bb.getInt(6), bb.get(10)) ;
	}

	/**
	 * @return time in seconds since 6 Jan 1980
	 */
	public long getCoarseTime() {
		return bb.getInt(6)&0xFFFFFFFFL;
	}
	public int getTimeId() {
		return (bb.get(11) &0xFF)>>6;
	}
	
	public void setCoarseTime(int time) {
	    bb.putInt(6, time);
	}
	
	public static long getCoarseTime(ByteBuffer bb) {
		return bb.getInt(6)&0xFFFFFFFFL;
	}
	
	public int getFineTime() {
		return bb.get(10)&0xFF;
	}
	
	public void setFineTime(short fineTime) {
	    bb.put(10, (byte)(fineTime&0xFF));
	}
	
	public static int getFineTime(ByteBuffer bb) {
		return bb.get(10)&0xFF;
	}
 	
	public boolean getChecksumIndicator() {
		return (bb.get(11) & 0x20) == 0x20;
	}

	public int getPacketID() {
		if(getSecondaryHeaderFlag() != 0)
			return bb.getInt(12);
		else
			return 0;
	}

	public void setPacketID(int id) {
	    bb.putInt(12, id);
	}



	public byte[] getBytes() {
		return bb.array();
	}
	
	public ByteBuffer getByteBuffer() {
		return bb;
	}

	public int getPrivateHeaderClass() {
		return bb.get(18);
	}

	public int getPrivateHeaderType() {
		return bb.get(19);
	}


	public int getPrivateHeaderSource() {
		return ((int)bb.get(16))&0xFF;
	}


	public static CcsdsPacket getPacketFromStream(InputStream input) throws IOException {
		byte[] b=new byte[6];
		ByteBuffer bb=ByteBuffer.wrap(b);
		if ( input.read(b) < 6 ) {
			throw new IOException("cannot read CCSDS primary header\n");
		}
		int ccsdslen = bb.getShort(4)&0xFFFF;
		if ( ccsdslen  > MAX_CCSDS_SIZE ) {
			throw new IOException("illegal CCSDS length "+ ccsdslen);
		}
		bb=ByteBuffer.allocate(ccsdslen+7);
		bb.put(b);

		if ( input.read(bb.array(), 6, ccsdslen+1) < ccsdslen+1 ) {
			throw new IOException("cannot read full packet");
		}
		return new CcsdsPacket(bb);
	}
	
	
	public static long getInstant(byte[] pkt) {
        return getInstant(ByteBuffer.wrap(pkt));
    }
	
	
	
	public static short getAPID(byte[] packet) {
	    return getAPID(ByteBuffer.wrap(packet));
	}
	
	public static int getCccsdsPacketLength(byte[] buf) {       
        return getCccsdsPacketLength(ByteBuffer.wrap(buf));
    }

    /*comparison based on time*/
    @Override
    public int compareTo(CcsdsPacket p) {
        return Long.signum(this.getInstant()-p.getInstant());
    }
	
	@Override
    public String toString() {
		StringBuffer sb=new StringBuffer();
		StringBuffer text = new StringBuffer();
		byte c;
		int len = bb.limit();
		sb.append("apid: "+getAPID()+"\n");
		sb.append("packetId: "+getPacketID()+"\n");
		sb.append("time: "+ TimeEncoding.toCombinedFormat(getInstant()));
		sb.append("\n");
		for(int i=0;i<len;++i) {
			if(i%16==0) {
				sb.append(String.format("%04x:",i));
				text.setLength(0);
			}
			c = bb.get(i);
			if ( (i & 1) == 0 ) {
				sb.append(String.format(" %02x",0xFF&c));
			} else {
				sb.append(String.format("%02x",0xFF&c));
			}
			text.append(((c >= ' ') && (c <= 127)) ? String.format("%c",c) : ".");
			if((i+1)%16==0) {
				sb.append(" ");
				sb.append(text);
				sb.append("\n");
			}
		}
		sb.append("\n\n");
		return sb.toString();
	}

}
