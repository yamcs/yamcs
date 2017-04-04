package org.yamcs.yarch;

import java.io.DataOutput;
import java.io.IOException;

/**
 * Serializes column values to byte arrays (used as part of tables) and back
 * @author nm
 *
 */
public interface ColumnSerializer<T> {
    /*
     * enums are deserialized as shorts 
     * (it is converted to the actual type in the {@link TableDefinition#deserialize(byte[], byte[])})
     */
    T deserialize(java.io.DataInput stream) throws IOException ;
    
    /**
     * @throws IOException
     */
    public void serialize(DataOutput stream, T v) throws IOException ;    
    public byte[] getByteArray(T v);
   
    public T fromByteArray(byte[] b) throws IOException ;
    
}
