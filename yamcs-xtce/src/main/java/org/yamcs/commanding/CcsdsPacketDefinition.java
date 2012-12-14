package org.yamcs.commanding;

import java.io.Serializable;
import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Definition of a TcPacket
 * @author mache
 *
 */
public class CcsdsPacketDefinition implements Serializable{
	
	private static final long serialVersionUID = 200611051707L;
	protected int sid;
	//primary header fields
	protected String opsName;
	protected String pathName;
	protected byte phVersion; 
	protected byte phType;
	protected byte phSecondaryHederFlag;
	protected int apid;
	protected byte  phSequcenceFlags;
	protected int phPacketLength;
	
	//secondary header fields
	protected byte shTimeId;
	protected int shChecksumIndicator;
	protected byte shPacketType;
	protected int shPacketId;
	
	transient Logger log=LoggerFactory.getLogger(this.getClass().getName());
	/**
	 * @return the apid
	 */
	public int getApid() {
		return apid;
	}
	/**
	 * @param apid the apid to set
	 */
	public void setApid(int apid) {
		this.apid = apid;
	}
	/**
	 * @return the opsname
	 */
	public String getOpsName() {
		return opsName;
	}
	/**
	 * @param opsname the opsname to set
	 */
	public void setOpsName(String opsName) {
		this.opsName = opsName;
	}
	public void setPathName(String pn) {
		this.pathName=pn;
	}
	
	public String getPathName() {
		return pathName;
	}
	/**
	 * @return the parameterAlignmentType
	 */
	//public ParameterAlignmentType getParameterAlignmentType() {
//		return parameterAlignment;
//	}
	/**
	 * @param parameterAlignmentType the parameterAlignmentType to set
	 */
	//public void setParameterAlignmentType(
	//		ParameterAlignmentType parameterAlignmentType) {
	//	this.parameterAlignment = parameterAlignmentType;
	//}
	/**
	 * @return the phPacketLength
	 */
	public int getPhPacketLength() {
		return phPacketLength;
	}
	/**
	 * @param phPacketLength the phPacketLength to set
	 */
	public void setPhPacketLength(int phPacketLength) {
		this.phPacketLength = phPacketLength;
	}
	/**
	 * @return the phSequcenceFlags
	 */
	public byte getPhSequcenceFlags() {
		return phSequcenceFlags;
	}
	/**
	 * @param phSequcenceFlags the phSequcenceFlags to set
	 */
	public void setPhSequcenceFlags(byte phSequcenceFlags) {
		this.phSequcenceFlags = phSequcenceFlags;
	}
	/**
	 * @return the phType
	 */
	public byte getPhType() {
		return phType;
	}
	/**
	 * @param phType the phType to set
	 */
	public void setPhType(byte phType) {
		this.phType = phType;
	}
	public byte getPhSecondaryHederFlag() {
		return phSecondaryHederFlag;
	}
	public void setPhSecondaryHederFlag(byte phSecondaryHederFlag) {
		this.phSecondaryHederFlag = phSecondaryHederFlag;
	}
	
	public void setPhSecondaryHederFlag(String phSecondaryHederFlagString) {
		if("TRUE".equals(phSecondaryHederFlagString)) {
			phSecondaryHederFlag = 1;
		} else {
			phSecondaryHederFlag = 0;
			log.error("ccsds packets without secondary headers not supported");
		}
	}
	/**
	 * @return the phVersion
	 */
	public byte getPhVersion() {
		return phVersion;
	}
	/**
	 * @param phVersion the phVersion to set
	 */
	public void setPhVersion(byte phVersion) {
		this.phVersion = phVersion;
	}
	/**
	 * @return the shPacketId
	 */
	public int getShPacketId() {
		return shPacketId;
	}
	/**
	 * @param shPacketId the shPacketId to set
	 */
	public void setShPacketId(int shPacketId) {
		this.shPacketId = shPacketId;
	}
	/**
	 * @return the shPacketType
	 */
	public byte getShPacketType() {
		return shPacketType;
	}
	/**
	 * @param shPacketType the shPacketType to set
	 */
	public void setShPacketType(byte shPacketType) {
		this.shPacketType = shPacketType;
	}
	/**
	 * @return the shTimeID
	 */
	public byte getShTimeID() {
		return shTimeId;
	}
	/**
	 * @param shTimeID the shTimeID to set
	 */
	public void setShTimeID(byte shTimeID) {
		this.shTimeId = shTimeID;
	}
	/**
	 * @return the sid
	 */
	public int getSid() {
		return sid;
	}
	/**
	 * @param sid the sid to set
	 */
	public void setSid(int sid) {
		this.sid = sid;
	}
	
	public void setShTimeID(String s) {
		if("NO_TIME_FIELD".equals(s)) {
			shTimeId=SH_TIME_ID_NO_TIME_FIELD;
		} else if("TIME_OF_PACKET_GENERATION".equals(s)){
			shTimeId=SH_TIME_ID_TIME_OF_PACKET_GENERATION;
		} else if("TIME_TAG".equals(s)) {
			shTimeId=SH_TIME_ID_TIME_TAG;
		} else if("UNDEFINED".equals(s)){
			shTimeId=SH_TIME_ID_UNDEFINED;
		} else {
			log.error("Unknown Secondary Header Time id:"+s+" for opsname="+opsName);
		}
	}
	public void setShPacketType(String s) {
		if("CCSDS_PAYLOAD_COMMAND_PACKET".equals(s)){
			shPacketType=SH_PKT_TYPE_CCSDS_PAYLOAD_COMMAND_PACKET;
		} else if("CCSDS_PAYLOAD_HK_PACKET".equals(s)){
			shPacketType=SH_PKT_TYPE_CCSDS_CCSDS_PAYLOAD_HK_PACKET;
		} else if("CCSDS_RESPONSE_PACKET".equals(s)){
			shPacketType=SH_PKT_TYPE_CCSDS_RESPONSE_PACKET;
		} else if("CCSDS_MEMORY_LOAD_PACKET".equals(s)){
			shPacketType=SH_PKT_TYPE_CCSDS_MEMORY_LOAD_PACKET;
		} else {
			log.error("command packet type="+s+" not supported");
		}
	}

	public int getShChecksumIndicator() {
		return shChecksumIndicator;
	}
	public void setShChecksumIndicator(int checksumIndicator) {
		this.shChecksumIndicator = checksumIndicator;
	}
	public void setShChecksumIndicator(String s) {
		if("TRUE".equals(s)){
			shChecksumIndicator=1;
		} else {
			shChecksumIndicator=0;
		}
	}
	
	/**
	 * Fills in the ccsds primary and secondary headers with the time and sequence passed as arguments.
	 * @param bb  byte buffer that will be modified (starting with byte 0)
	 * @param seq sequence count (has to be incremented per APID)
	 * @param phPacketLength the packet length inserted into the primary header. The packet has actualy to be +7 bytes in length
	 * @param timeMilisec time as returned by System.currentTimeMillis() (miliseconds from 1 Jan 1970). It will be converted in CUC time (seconds from 6 Jan 1980) 
	 * 
	 */
	public void fillInHeaders(ByteBuffer bb, int seqCount, int phPacketLength, long timeMilisec) {
		short xs;
		//Primary header:
		// version(3bits) type(1bit) secondary header flag(1bit) apid(11 bits)
		xs=(short)((phVersion<<13)|(phType<<12)|(1<<11)|apid);
		bb.putShort(0,xs);
		//Seq Flags (2 bits) Seq Count(14 bits)
		xs=(short) ((phSequcenceFlags<<14)|seqCount);
		bb.putShort(2,xs);
		//packet length (16 bits).
		bb.putShort(4,(short)(phPacketLength));
		
		//Secondary header:
		//coarse time(32 bits)
		bb.putInt(6, (int) (timeMilisec/1000-315964800L));
		//fine time(8 bits) timeID(2bits) checkword(1 bit) spare(1 bit) pktType(4 bits)
		xs=(short)((shTimeId<<6)|(shChecksumIndicator<<5)|shPacketType);
		bb.putShort(10,xs);
		//packetId(32 bits)
		bb.putInt(12,shPacketId);
	}
	
	public String toString(){
		return "opsname="+opsName+" phVersion="+phVersion+" phType="+phType+" apid="+apid+" phSequcenceFlags="+phSequcenceFlags+
				" phPacketLength="+phPacketLength+" shTimeId="+shTimeId+" shChecksumIndicator="+shChecksumIndicator+
				" shPacketType="+shPacketType+"shPacketId="+shPacketId;
	}
	
	final int SH_TIME_ID_NO_TIME_FIELD=0;
	final int SH_TIME_ID_TIME_OF_PACKET_GENERATION=1;
	final int SH_TIME_ID_TIME_TAG=2;
	final int SH_TIME_ID_UNDEFINED=3;
	
	final int SH_PKT_TYPE_CCSDS_CCSDS_PAYLOAD_HK_PACKET =  5;
	final int SH_PKT_TYPE_CCSDS_PAYLOAD_COMMAND_PACKET =  10;
	final int SH_PKT_TYPE_CCSDS_MEMORY_LOAD_PACKET = 11;
	final int SH_PKT_TYPE_CCSDS_RESPONSE_PACKET =  12;
	

}
