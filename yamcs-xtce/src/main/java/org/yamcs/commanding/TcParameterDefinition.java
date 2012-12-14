package org.yamcs.commanding;

import java.io.Serializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the definition of a tc parameter
 * Not being constrained by the corba stuff like in the TM case, we use a more traditional approach 
 * with an object of type Object which can be assigned either a Long, String, Double or byte[]
 * To simplify the code, we use the following relationships between UCL types and java:
 * UCL                       JAVA
 * BYTE_TYPE                 Long
 * INTEGER_TYPE              Long
 * UNSIGNED_INTEGER_TYPE     Long
 * REAL_TYPE				 Double
 * STRING_TYPE				 byte[]
 * BYTE_STRING_TYPE			 byte[]
 * STATE_CODE_TYPE			 String
 * 
 * the BYTE, INTEGER, UNSIGNED_INTEGER can have maximum and minimum values 
 * STRING and BYTE_STRING can have maximum length 
 * 
 * TODO: replace with an XTCE equivalent
 * @author mache
 *
 */
public class TcParameterDefinition implements Serializable{

	private static final long serialVersionUID = 200706291619L;
	TcPacketDefinition tcPacketDefinition;
	String name;
	int location;
	int numberOfBits;
	public enum SwTypes {BYTE_TYPE, UNSIGNED_INTEGER_TYPE, INTEGER_TYPE, REAL_TYPE, STRING_TYPE, BYTE_STRING_TYPE, STATE_CODE_TYPE};
	SwTypes engType;
	SwTypes rawType;
	private Object defaultValue=null; //by default no default value
	private Object minValue=null; //by default the minimum value allowed by the number of bits or 0 for unsigned types
	private Object maxValue=null; //by default the maximum value allowed by the number of bits
	private int maxLength=-1; //for strings the maximum lengths in bytes allowed by the number of bits (i.e. numberOfBits/8)
	public enum StringTypes {FIXED_LENGTH,VARIABLE_LENGTH};
	StringTypes stringType=StringTypes.FIXED_LENGTH;
	transient static Logger log=LoggerFactory.getLogger(TcParameterDefinition.class.getName());
	Decalibration decalibration;
	
	
	
	public void setMaxValue(Object maxValue) {
		this.maxValue = maxValue;
	}

	public void setMinValue(Object minValue) {
		this.minValue = minValue;
	}
	/**
	 * @return the defaultIntegerValue
	 */
	public Object getDefaultValue() {
		return defaultValue;
	}
	/**
	 * @param defaultIntegerValue the defaultIntegerValue to set
	 */
	public void setDefaultValue(Object defaultValue) {
		this.defaultValue = defaultValue;
	}
	/**
	 * @return the location
	 */
	public int getLocation() {
		return location;
	}
	/**
	 * @param location the location to set
	 */
	public void setLocation(int location) {
		this.location = location;
	}
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}
	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}
	/**
	 * @return the numberOfBits
	 */
	public int getNumberOfBits() {
		return numberOfBits;
	}
	/**
	 * @param numberOfBits the numberOfBits to set
	 */
	public void setNumberOfBits(int numberOfBits) {
		this.numberOfBits = numberOfBits;
	}
	/**
	 * @return the rawType
	 */
	public SwTypes getRawType() {
		return rawType;
	}
	/**
	 * @param rawType the rawType to set
	 */
	public void setRawType(SwTypes rawType) {
		this.rawType = rawType;
	}
	/**
	 * @return the engType
	 */
	public SwTypes getEngType() {
		return engType;
	}
	/**
	 * @param engType the engType to set
	 */
	public void setEngType(SwTypes engType) {
		this.engType = engType;
	}
	/**
	 * @return the tcPacketDefinition
	 */
	public TcPacketDefinition getTcPacketDefinition() {
		return tcPacketDefinition;
	}
	/**
	 * @param tcPacketDefinition the tcPacketDefinition to set
	 */
	public void setTcPacketDefinition(TcPacketDefinition tcPacketDefinition) {
		this.tcPacketDefinition = tcPacketDefinition;
	}
	
	public void setRawType(String s) {
		if(s==null) {
			log.error("No raw type specified for TC parameter "+tcPacketDefinition.getOpsName()+" ("+name+")"+", setting it to INTEGER_TYPE");
			rawType=SwTypes.INTEGER_TYPE;
			return;
		}	
		try {
			rawType=SwTypes.valueOf(s);
		} catch (IllegalArgumentException e) {
			rawType=SwTypes.INTEGER_TYPE;
			log.error("Tc Parameter type '"+s+"' not supported for "+tcPacketDefinition.getOpsName()+" ("+name+")"+" setting it to INTEGER_TYPE");
		}
	}

	public void setEngType(String s) {
		if(s==null) {
			log.error("No eng type specified for TC parameter "+tcPacketDefinition.getOpsName()+" ("+name+")"+", setting it to INTEGER_TYPE");
			engType=SwTypes.INTEGER_TYPE;
			return;
		}	
		try {
			engType=SwTypes.valueOf(s);
		} catch (IllegalArgumentException e) {
			engType=SwTypes.INTEGER_TYPE;
			log.error("Tc Parameter eng type '"+s+"' not supported for "+tcPacketDefinition.getOpsName()+" ("+name+")"+" setting it to INTEGER_TYPE");
		}
		//we initialize the rawType with engType, it will be changed later if required
		rawType=engType;
	}
	
	/**
	 * returns the maximum value when the type is integer like (i.e. byte, integer, or unsigned integer)
	 * @return
	 */	
	public Object getMaxValue() {
		switch (engType) {
		case BYTE_TYPE:
		case UNSIGNED_INTEGER_TYPE:
			if(maxValue==null)
				return  Long.valueOf((1L<<numberOfBits)-1L);
			else
				return maxValue;
		case INTEGER_TYPE:
			if(maxValue==null)
				return Long.valueOf((1L<<(numberOfBits-1))-1);
			else
				return maxValue;
		case REAL_TYPE:
			if (maxValue==null) 
				return Double.MAX_VALUE; 
			else 
				return maxValue;
		default:
			return null;
		}
	}
	/**
	 * returns the minimum value
	 * @return
	 */
	public Object getMinValue() {
		switch (engType) {
		case BYTE_TYPE:
		case UNSIGNED_INTEGER_TYPE:
			if(minValue==null)
				return  Long.valueOf(0);
			else
				return minValue;
		case INTEGER_TYPE:
			if(minValue==null)
				return Long.valueOf(-1L<<(numberOfBits-1));
			else
				return minValue;
		case REAL_TYPE:
			if (minValue==null) 
				return -Double.MAX_VALUE; 
			else 
				return minValue;
		default:
			return 0;
		}
	}
	
	/** 
	 * sets the maximum length of the string or byte string
	 * @param x
	 */
	public void setMaxLength(int x) {
		maxLength=x;
	}
	public int getMaxLength() {
		if(maxLength!=-1)
			return maxLength;
		else 
			return numberOfBits/8;
	}
	
	
	/**
	 * Sets the type of string for the parameters of type string
	 * @param string one of VARIABLE_LENGTH or FIXED_LENGTH
	 */
	public void setStringType(String s) {
		if(s==null) {
			log.error("No string type specified for TC parameter "+tcPacketDefinition.getOpsName()+" ("+name+")"+", defaulting it to "+stringType);
			return;
		}
		stringType=StringTypes.valueOf(s);
	}
	
	public Decalibration getDecalibration() {
		return decalibration;
	}
	
	public void setDecalibration(Decalibration d) {
		this.decalibration=d;
	}
	
	public Object decalibrate(Object engValue) throws DecalibrationNotSupportedException {
		if(decalibration==null) return engValue; 
		return decalibration.decalibrate(engValue, getRawType(), getEngType());
	}
	
	@Override
    public String toString() {
		StringBuffer sb=new StringBuffer();
		sb.append(name);sb.append(" eng type="+engType);
		switch (engType) {
		case BYTE_STRING_TYPE:
		case STRING_TYPE:
			sb.append(" stringType="+stringType+" maxLength="+maxLength);
			break;
		case STATE_CODE_TYPE:
			sb.append("STATE_CODE");
			break;
		default:
			sb.append(" minValue="+getMinValue()+" maxValue="+getMaxValue());
		}
		sb.append(", raw type="+rawType);
		sb.append(", encoding: location="+location);
		sb.append(" nobits="+numberOfBits);
		sb.append(", decalibration: "+decalibration);
		if(defaultValue!=null)  {
		    	sb.append(", defaultValue: ");
			switch (engType) {
			case STRING_TYPE:
				sb.append("'"+new String((byte[]) defaultValue)+"'");
				break;
			default:
				sb.append(defaultValue);
			}
		}
		return sb.toString();
	}
}
