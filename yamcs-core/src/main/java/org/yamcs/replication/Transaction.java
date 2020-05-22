package org.yamcs.replication;

import java.nio.ByteBuffer;

public interface Transaction {
    
    static boolean isMetadata(byte type) {
        return type == Message.STREAM_INFO;
    }
    
    byte getType();
    void marshall(ByteBuffer buf);

    int getInstanceId();
}
