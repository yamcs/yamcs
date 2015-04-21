package org.yamcs.xtce;

/**
 * For binary data or for integer, float, string, or time data that is not in any of the known encoding formats.  
 * For any data that is not encoded in any of the known integer, float, string, or time data formats use a To/From transform algorithm.
 * @author mache
 *
 */
public class BinaryDataEncoding extends DataEncoding {
    private static final long serialVersionUID=200805131551L;
    public BinaryDataEncoding(String name, int sizeInBits) {
        super(name, sizeInBits);
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
