package org.yamcs.replication;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public interface Transaction {
    
    static boolean isMetadata(byte type) {
        return type == Message.STREAM_INFO;
    }
    
    byte getType();
    void marshall(ByteBuffer buf) throws BufferOverflowException;

    int getInstanceId();
}
