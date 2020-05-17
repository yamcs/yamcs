package org.yamcs.replication;

import java.nio.ByteBuffer;

public class ReplicationTail {
    ByteBuffer buf;
   
    boolean eof;//end of file
    public long nextTxId;
    
    @Override
    public String toString() {
        return "ReplicationTail [buf.pos=" + buf.position()+", buf.limit="+buf.limit() + ", eof=" + eof + ", nextTxId=" + nextTxId + "]";
    }
}
