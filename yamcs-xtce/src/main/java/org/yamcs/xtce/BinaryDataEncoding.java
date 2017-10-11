package org.yamcs.xtce;


/**
 * For binary data or for integer, float, string, or time data that is not in any of the known encoding formats.  
 * For any data that is not encoded in any of the known integer, float, string, or time data formats use a To/From transform algorithm.
 * 
 * DIFFERS_FROM_XTCE: XTCE doesn't support LEADING_SIZE parameter types (it does only for strings). However it allows the size to be specified dynamically
 * by the value of another parameter. 
 *
 */
public class BinaryDataEncoding extends DataEncoding {
    private static final long serialVersionUID = 2L;
    
    public enum Type {FIXED_SIZE, LEADING_SIZE, CUSTOM}
    
    int sizeInBitsOfSizeTag = 16; //this is used when type is LEADING_SIZE to encod the length of the value before the value
    private Type type;

    
    public BinaryDataEncoding(Type type) {
        super(-1);
        this.type=type;
    }
    
    public BinaryDataEncoding(int sizeInBits) {
        super(sizeInBits);
        this.type = Type.FIXED_SIZE;
    }


    public void setSizeType(Type sizeType) {
        this.type = sizeType;
    }
    public Type getType() {
        return type;
    }

    public int getSizeInBitsOfSizeTag(){
        return sizeInBitsOfSizeTag;
    }

    public void setSizeInBitsOfSizeTag(int sizeInBits){
        this.sizeInBitsOfSizeTag=sizeInBits;
    }

    @Override
    public String toString() {
        return "BinaryDataEncoding(sizeInBits:"+sizeInBits+")";
    }

    @Override
    public Object parseString(String stringValue) {
        return BinaryDataType.hexStringToArray(stringValue);
    }
}
