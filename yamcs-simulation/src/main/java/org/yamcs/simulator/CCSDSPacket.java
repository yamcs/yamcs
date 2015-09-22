package org.yamcs.simulator;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;

public class CCSDSPacket {
    
    /*
        primary header (6 bytes):
        	3 bit = version
        	1 bit = type (0 = system packet, 1 = payload packet)
        	1 bit = 2nd header present
        	11 bit = apid
        
        	2 bit = grouping, 01 = first, 00 = cont, 10 = last packet of group
        	14 bit = seq
        
        	16 bit = packet length (excluding primary header) minus 1
        
        secondary header (10 bytes):
        	32 bit = coarse time (seconds since 1970)
        	8 bit = fine time
        	2 bits = time id (see constants)
        	1 bit = checksum present (2 bytes after user data)
        	5 bits = packet type (see constants)
        	32 bit = packet id
    */
    
	// Header Attributes 
	int apid, seq, packetid;
	int packetType;
	private long timeMillis;
	protected ByteBuffer buffer;
	private short w;
	
	final byte SH_TIME_ID_NO_TIME_FIELD = 0;
	final byte SH_TIME_ID_TIME_OF_PACKET_GENERATION = 1;
	final byte SH_TIME_ID_TIME_TAG = 2;
	final byte SH_TIME_ID_UNDEFINED = 3;
	
	//Packet types 
	final byte SH_PKT_TYPE_CCSDS_CCSDS_PAYLOAD_HK_PACKET =  5;
	final byte SH_PKT_TYPE_CCSDS_PAYLOAD_COMMAND_PACKET = 10;
	final byte SH_PKT_TYPE_CCSDS_MEMORY_LOAD_PACKET = 11;
	final byte SH_PKT_TYPE_CCSDS_RESPONSE_PACKET = 12;

	public CCSDSPacket(ByteBuffer buffer) {
		this.buffer = buffer;
		apid = buffer.getShort(0) & 0x07ff;
		seq = buffer.getShort(2) & 0x3fff;
		packetType = (byte) (buffer.get(11) & 0x0F); // get the  packet type
		packetid = buffer.getInt(12);
		timeMillis = ((long)(buffer.getInt(6)) + 315964800L)*1000 + (long)(buffer.get(10))*1000/256;
	}

	public CCSDSPacket(int userDataLength, int packetid) {
		apid = 1;
		seq = 0;
		timeMillis = System.currentTimeMillis();
		this.packetType = SH_PKT_TYPE_CCSDS_CCSDS_PAYLOAD_HK_PACKET;
		this.packetid = packetid;
		buffer = ByteBuffer.allocate(userDataLength + 16);
		buffer.order(ByteOrder.BIG_ENDIAN);

		putHeader();
	}
	
	public CCSDSPacket(int userDataLength, int packetType, int packetid) {		
		apid = 1;
		seq = 0;
		timeMillis = System.currentTimeMillis();
		buffer = ByteBuffer.allocate(userDataLength + 16);
		buffer.order(ByteOrder.BIG_ENDIAN);
		
		this.packetType = packetType;
		this.packetid = packetid;
		
		putHeader();
	}

	private void putHeader() {
		// primary

		w = (short) ((1 << 11) | apid); // 2nd header present
		buffer.putShort(0, w);
		seq = getSeq(apid);
		buffer.putShort(2, (short) (seq & 0x3fff));
		buffer.putShort(4, (short) (buffer.capacity() - 7)); // secondary
		buffer.putInt(6, (int) (timeMillis / 1000 - 315964800L)); // epoch  starts a 06-Jan-1980 00:00:00
		buffer.put(10, (byte) ((timeMillis % 1000) * 256 / 1000));
		//buffer.put(11, (byte)((SH_TIME_ID_TIME_OF_PACKET_GENERATION<<6)|SH_PKT_TYPE_CCSDS_CCSDS_PAYLOAD_HK_PACKET)); // original version with no checksum;
		buffer.put(11, (byte) ((SH_TIME_ID_TIME_OF_PACKET_GENERATION << 6) | packetType)); // no checksum id		   // modified to allow for packet type assignment 
		buffer.putInt(12, packetid);
		//describePacketHeader();
	}

	public ByteBuffer getUserDataBuffer() {
		buffer.position(16);
		return buffer.slice();
	}

    public void appendUserDataBuffer(byte[] userData){
        this.buffer = ByteBuffer.allocate(this.buffer.capacity() + userData.length).put(this.buffer.array()).put(userData);
        updatePacketSize();
    }

    public void setUserDataBuffer(ByteBuffer buffer) {
        this.buffer = ByteBuffer.allocate(this.buffer.capacity())
                .put(this.buffer).put(buffer);
        updatePacketSize();
    }

    private void updatePacketSize() {
        // update the packet size information in the secondary header
        this.buffer.putShort(4, (short) (this.buffer.capacity() - 7));
    }

	public void writeTo(OutputStream os) throws IOException {
		try {
			if (buffer.hasArray()) {
				os.write(buffer.array());
			//	System.out.println(HexDump.dumpHexString(buffer.array()));
			//	System.out.println("sent " + this + " " + payload);
			}
		} catch (BufferOverflowException e) {
			System.out.println("overflow while sending "+this);
		}
	}
	
	static HashMap<Integer,Integer> seqMap = new HashMap<>(2); // apid -> seq

	private static int getSeq(int apid) {
		int seq = seqMap.containsKey(apid) ? seqMap.get(apid) : 0;
		seqMap.put(apid, seq);
		return seq;
	}
	
	 public void describePacketHeader(int byteToRead){
		 String s1 = String.format("%8s", Integer.toBinaryString(byteToRead & 0xFF)).replace(' ', '0');
		 System.out.println("::" + s1);
	 }
	 
	 public int getPacketId() {
	     return packetid;
	 }
	 
	 public void setPacketId(int packetId) {
	     this.packetid = packetId;
	 }
	 
	 public int getPacketType() {
	     return packetType;
	 }
	 
	 @Override
	 public String toString() {
		StringBuffer sb=new StringBuffer();
		sb.append("apid: "+apid+"\n");
		sb.append("seq: "+seq+"\n");
		sb.append("packetId: "+packetid+"\n");
		sb.append("packetType: "+packetType+"\n");
		sb.append("time: "+ timeMillis);
		sb.append("\n");
		
		return sb.toString();
	}
}
