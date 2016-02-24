package org.yamcs.parameter;

public abstract class Value {
    public abstract org.yamcs.protobuf.Yamcs.Value.Type getType();
    
    public int getUint32Value() {
        throw new UnsupportedOperationException();
    }
    
    public int getSint32Value() {
        throw new UnsupportedOperationException();
    }
    
    public long getUint64Value() {
        throw new UnsupportedOperationException();
    }
    
    public long getSint64Value() {
        throw new UnsupportedOperationException();
    }
    
    public byte[] getBinaryValue() {
        throw new UnsupportedOperationException();
    }
    
    public  String getStringValue() {
        throw new UnsupportedOperationException();
    }
    
    public float getFloatValue() {
        throw new UnsupportedOperationException();
    }
    
    public double getDoubleValue() {
        throw new UnsupportedOperationException();
    }
    
    public boolean getBooleanValue() {
        throw new UnsupportedOperationException();
    }
    
    public long getTimestampValue() {
        throw new UnsupportedOperationException();
    }
}
