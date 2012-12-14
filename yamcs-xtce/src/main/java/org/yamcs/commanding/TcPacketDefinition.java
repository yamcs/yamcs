package org.yamcs.commanding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;


/**
 * Definition of a TcPacket. TODO: replace with an XTCE equivalent
 * @author mache
 *
 */
public class TcPacketDefinition extends CcsdsPacketDefinition implements Serializable {
	
	private static final long serialVersionUID = 200706051309L;

	//location, legnth, etc
	//enum  LocationSpecificationModes {ABSOLUTE, ALIGNED, RELATIVE} ;
	enum  LocationSpecificationModes {ABSOLUTE, RELATIVE} ; //we don't support ALIGNED
	private LocationSpecificationModes locationSpecificationMode;
	
	private int stringLengthFieldSize;
	
	private ArrayList<TcParameterDefinition> parameterList = new ArrayList<TcParameterDefinition>();
	private ArrayList<BitstreamLayoutDefinition> bitstreamLayoutList=new ArrayList<BitstreamLayoutDefinition>();
	
	transient Logger log=LoggerFactory.getLogger(this.getClass().getName());
	private ArrayList<String> privileges;
	
	
	/**
	 * @return the locationSpecificationMode
	 */
	public LocationSpecificationModes getLocationSpecificationMode() {
		return locationSpecificationMode;
	}
	/**
	 * @param locationSpecificationMode the locationSpecificationMode to set
	 */
	public void setLocationSpecificationMode(
			LocationSpecificationModes locationSpecificationMode) {
		this.locationSpecificationMode = locationSpecificationMode;
	}
	/**
	 * @return the phPacketLength
	 */
	/**
	 * @return the stringLengthFieldSize
	 */
	public int getStringLengthFieldSize() {
		return stringLengthFieldSize;
	}
	/**
	 * @param stringLengthFieldSize the stringLengthFieldSize to set
	 */
	public void setStringLengthFieldSize(int l) {
		if((l!=0) && (l!=8)&&(l!=16)&&(l!=32)) {
			log.error("String Length Field Size "+l+" not supported (param="+opsName+")");
		}
		this.stringLengthFieldSize = l;
	}
	
	public void setLocationSpecificationMode(String s) {
		if(s!=null){
			locationSpecificationMode=LocationSpecificationModes.valueOf(s);
		} else {
			locationSpecificationMode=LocationSpecificationModes.RELATIVE;
			log.warn("locationSpecificationMode for "+opsName +"("+pathName+") not specified, defaulting to "+locationSpecificationMode);
		}
	}
	
	public void setParameterAlignment(String s) {
		if(s!=null) {
			System.err.println("Parameter alignment not supported");
			System.exit(-1);
		}
	}
	
	public void addParameter(TcParameterDefinition tparamd) {
		parameterList.add(tparamd);
	}	

	public void addBitstreamLayoutDefinition(int location, String format,  int unsignedIntegerValue, int numberOfBits, 
                	String binaryValueType, String binaryValue) {
		BitstreamLayoutDefinition bld=new BitstreamLayoutDefinition();
		bld.location=location;
		bld.format=BitstreamLayoutDefinition.Formats.valueOf(format);
		switch(bld.format) {
		case UNSIGNED:
			bld.setNumberOfBits(numberOfBits);
			bld.unsignedIntegerValue=unsignedIntegerValue;
		break;
		case BINARY:
			if(binaryValueType==null) {
				log.error("binaryValueType is null for command "+opsName+"("+pathName+")");
				return;
			}
			bld.binaryValueType=BitstreamLayoutDefinition.BinaryValueTypes.valueOf(binaryValueType);
			switch (bld.binaryValueType) {
			case ASCII:
				bld.setBinaryValue(binaryValue.getBytes());
				break;
			case HEX:
				byte[] b=new byte[binaryValue.length()/2];
				for(int i=0;i<binaryValue.length()/2;i++) {
					b[i]=(byte)(Integer.parseInt(binaryValue.substring(2*i,2*i+2),16)&0xFF);
				}
				bld.setBinaryValue(b);
			}
			
		break;
		}
		bitstreamLayoutList.add(bld);	
	}

	/**
	 * Adds the privilege to the list of privileges, creating the list if it's null
	 * @param priv
	 */
	public void addPrivilege(String priv) {
		if(privileges==null) privileges=new ArrayList<String>();
		privileges.add(priv);
		
	}
	
	/**
	 * @return the list of privileges required to send this command
	 */
	public Collection<String> getPrivileges() {
		return privileges;
	}
	
	
	public TcParameterDefinition getParameter(String paraname) {
		for(TcParameterDefinition t:parameterList) {
			if(t.name.equalsIgnoreCase(paraname)) return t;
		}
		return null;
	}
	public TcParameterDefinition getParameter(int i) {
		return parameterList.get(i);
	}
	public List<BitstreamLayoutDefinition> getBitstreamLayoutList() {
		return bitstreamLayoutList;
	}
	public ArrayList<TcParameterDefinition> getParameterList() {
		return parameterList;
	}
	
	@Override
    public String toString(){
        StringBuffer sb=new StringBuffer();
        sb.append("tc "+opsName+" apid="+apid+" pktId="+shPacketId+" shChecksumIndicator: "+shChecksumIndicator+" with bitstream layout:\n");
        for(Iterator<BitstreamLayoutDefinition> it=bitstreamLayoutList.iterator();it.hasNext();) {
            sb.append("\t");
            sb.append(it.next().toString());
            sb.append("\n");
        }
        sb.append("    and parameters:\n");
        System.out.println(opsName);
        for(Iterator<TcParameterDefinition> it=parameterList.iterator();it.hasNext();) {
            sb.append("\t");
            sb.append(it.next().toString());
            sb.append("\n");
        }
        return sb.toString();
    }
    
};
