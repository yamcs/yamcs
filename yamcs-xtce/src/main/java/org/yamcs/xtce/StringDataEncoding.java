package org.yamcs.xtce;

/**
 * For common encodings of string data
 * @author mache
 *
 */
public class StringDataEncoding extends DataEncoding {
	private static final long serialVersionUID=200805131551L;
	public enum SizeType {Fixed, TerminationChar, LeadingSize};
	private SizeType sizeType;
	private byte terminationChar=0;
	int sizeInBitsOfSizeTag=16;
	
	public StringDataEncoding(String name, SizeType sizeType) {
		super(name, -1);
		this.sizeType=sizeType;
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
        String out=getSizeType()+"(";
        switch(sizeType) {
        case Fixed:
            out+="fixedSizeInBits="+getSizeInBits();
            break;
        case LeadingSize:
            out+="sizeInBitsOfSizeTag="+sizeInBitsOfSizeTag;
            break;
        case TerminationChar:
            out+="terminationChar="+terminationChar;
            break;
        }
        return "StringDataEncoding size: "+out+")";

    }
}
