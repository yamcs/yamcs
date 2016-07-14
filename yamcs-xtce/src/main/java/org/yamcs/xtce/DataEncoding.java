package org.yamcs.xtce;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteOrder;

public abstract class DataEncoding extends NameDescription implements Serializable {
    private static final long serialVersionUID = 200805131551L;

    protected int sizeInBits;
    transient ByteOrder byteOrder=ByteOrder.BIG_ENDIAN; //DIFFERS_FROM_XTCE in xtce is bloody complicated
    
    DataEncoding(String name, int sizeInBits) {
        super(name);
        this.sizeInBits = sizeInBits;
    }
    
    DataEncoding(String name, int sizeInBits, ByteOrder byteOrder) {
        this(name, sizeInBits);
        this.byteOrder = byteOrder;
    }
    
    
    public int getSizeInBits() {
        return sizeInBits;
    }
    
    public void setSizeInBits(int sizeInBits) {
        this.sizeInBits = sizeInBits;
    }

    public ByteOrder getByteOrder() {
        return byteOrder;
    }
    public void setByteOrder(ByteOrder order) {
        this.byteOrder = order;        
    }

    //these two methods are used for serialisation because ByteOrder is not serializable
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        if(byteOrder==ByteOrder.BIG_ENDIAN) out.writeInt(0);
        else out.writeInt(1);
    }
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        int o=in.readInt();
        if(o==0) byteOrder=ByteOrder.BIG_ENDIAN;
        else byteOrder=ByteOrder.LITTLE_ENDIAN;
    }

    /**
     * parses the string into a java object of the correct type
     * Has to match the DataEncodingDecoder (so probably it should be moved there somehow: TODO)
     */
    public abstract Object parseString(String stringValue);
}
