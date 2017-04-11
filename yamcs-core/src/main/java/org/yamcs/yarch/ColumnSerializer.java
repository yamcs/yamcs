package org.yamcs.yarch;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Serializes column values to byte arrays (used as part of tables) and back
 * @author nm
 * @param <T> 
 *
 */
public interface ColumnSerializer<T> {
    /**
     *  Read one column value (i.e. a cell from the stream) 
     *  
     *  The enums are deserialized as shorts 
     * (it is converted to the actual type in the {@link TableDefinition#deserialize(byte[], byte[])})
     * @param stream - data stream used for the input
     * @param cd the column definition for the involved column (can be used to look up column name or other properties to help in deserialization)
     * @return the deserialized value
     * @throws IOException 
     */
    T deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException ;
    
    /**
     * @param stream 
     * @param v 
     * @throws IOException
     */    
    public void serialize(DataOutputStream stream, T v) throws IOException ;    
    
    /**
     * This method serializes the value into a byte array
     * @param v
     * @return the resulting byte array
     */
    public byte[] toByteArray(T v);
   
    /**
     * this method deserializes the value from a byte array
     * @param b the input byte array
     * @param cd the column definition for the involved column (can be used to look up column name or other properties to help in deserialization)
     * @return the deserialized value
     * @throws IOException
     */
    public T fromByteArray(byte[] b, ColumnDefinition cd) throws IOException ;
    
}
