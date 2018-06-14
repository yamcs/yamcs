package org.yamcs.xtce;

import java.nio.charset.Charset;

/**
 * For common encodings of string data
 * @author nm
 */
public class StringDataEncoding extends DataEncoding {
    private static final long serialVersionUID = 1L;
    public enum SizeType {
        /**
         * fixed size has to be specified in the {@link #getSizeInBits}
         */
        FIXED,
        /**
         * Like C strings, they are terminated with a special string, usually a null character.
         */
        TERMINATION_CHAR,
        /**
         * Like PASCAL strings, the size of the string is given as an integer at the start of the string.  SizeTag must be an unsigned Integer
         */
        LEADING_SIZE,
        /**
         * {@link #getFromBinaryTransformAlgorithm} will be used to decode the data
         */
        CUSTOM
    };
    private SizeType sizeType;
    private byte terminationChar = 0; //it's in fact the terminationByte but we call it like this for compatibility with XTCE
    int sizeInBitsOfSizeTag=16;
    
    private String encoding = "UTF-8";
    
    public StringDataEncoding() {
        super(-1);
    }
    
    
    public StringDataEncoding(SizeType sizeType) {
        super(-1);
        this.sizeType = sizeType;
    }

    public void setSizeType(SizeType sizeType) {
        this.sizeType = sizeType;
    }
    public SizeType getSizeType() {
        return sizeType;
    }

    public int getSizeInBitsOfSizeTag(){
        return sizeInBitsOfSizeTag;
    }

    public void setSizeInBitsOfSizeTag(int sizeInBits){
        this.sizeInBitsOfSizeTag=sizeInBits;
    }

    public byte getTerminationChar() {
        return terminationChar;
    }

    public void setTerminationChar(byte tc) {
        this.terminationChar = tc;
    }
    

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("StringDataEncoding size: ");        
        sb.append(getSizeType()).append("(");
        switch(getSizeType()) {
        case FIXED:
            sb.append("fixedSizeInBits="+getSizeInBits());
            break;
        case LEADING_SIZE:
            sb.append("sizeInBitsOfSizeTag="+getSizeInBitsOfSizeTag());
            if(getSizeInBits()!=-1) {
                sb.append(", minSizeInBits="+getSizeInBits());
            }
            break;
        case TERMINATION_CHAR:
            sb.append("terminationChar="+getTerminationChar());
            if(getSizeInBits()!=-1) {
                sb.append(", minSizeInBits="+getSizeInBits());
            }
            break;
        case CUSTOM:
            sb.append(getFromBinaryTransformAlgorithm());
            break;
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public Object parseString(String stringValue) {
        return stringValue;
    }


    public String getEncoding() {
        return encoding;
    }


    public void setEncoding(String encoding) {
        //this will throw an exception if the charset is not supported by java
        Charset.forName(encoding);
        
        this.encoding = encoding;
    }
}
