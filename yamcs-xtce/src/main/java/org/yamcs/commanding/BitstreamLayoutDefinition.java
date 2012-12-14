package org.yamcs.commanding;

import java.io.Serializable;

public class BitstreamLayoutDefinition implements Serializable {
	private static final long serialVersionUID = 1L;
	int location;
	enum Formats {UNSIGNED, BINARY};
	Formats format;
	private int numberOfBits;
	int unsignedIntegerValue;
	enum BinaryValueTypes {HEX, ASCII};
	BinaryValueTypes binaryValueType;
	private byte[] binaryValue;
	
	/**
	 * This method is copied from StringConvertors class
	 * @param b
	 * @return
	 */
   public static String arrayToHexString(byte[] b){
        StringBuffer sb =new StringBuffer();
        for (int i=0;i<b.length;i++) {
            String s=Integer.toString(b[i]&0xFF,16);
            if(s.length()==1) s="0"+s;
            sb.append(s.toUpperCase());
        }
        return sb.toString();
    }

    public void setNumberOfBits(int numberOfBits) {
        this.numberOfBits = numberOfBits;
    }

    public int getNumberOfBits() {
        return numberOfBits;
    }
    
    
    public void setBinaryValue(byte[] binaryValue) {
        this.binaryValue = binaryValue;
    }

    public byte[] getBinaryValue() {
        return binaryValue;
    }

    @Override
    public String toString() {
        return "location:" + location+", format: "+format
            + ((format==Formats.UNSIGNED) ? " unsignedIntegerValue: "+unsignedIntegerValue +", numberOfBits: "+getNumberOfBits():"") 
            + ((format==Formats.BINARY)?", binaryvalue: "+arrayToHexString(binaryValue):"");
    }
    
};