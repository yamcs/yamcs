package org.yamcs;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;

/**
 * Yamcs Exception (can be transported over HornetQ)
 * 
 * @author nm
 */
public class YamcsException extends Exception {
    private static final long serialVersionUID = 1L;
   
    private String type;
    private  byte[] extra; //an protobuf message which can be understood by the receiver based on the type
    
    public YamcsException(String message) {
        super(message);
    }

    public YamcsException(String message, Throwable t) {
        super(message, t);
    }
    
    public YamcsException(String type, String message, Message extra) {
        super(message);
        this.type=type;
        this.extra=extra.toByteArray();
    }
    
    public YamcsException(String type, String message) {
        super(message);
        this.type=type;
    }
    
    public YamcsException(String type, String message, byte[] extra) {
        super(message);
        this.type=type;
        this.extra=extra;
    }

    public byte[] getExtra() {
        return extra;
    }

    public String getType() {
        return type;
    }

    public MessageLite decodeExtra(MessageLite.Builder b) throws InvalidProtocolBufferException {
        return b.mergeFrom(extra).build();
    }

    @Override
    public String toString() {
        Throwable t = getCause();
        if (t != null)
            return getMessage() + ": " + t.toString();
        else
            return getMessage();
    }
}