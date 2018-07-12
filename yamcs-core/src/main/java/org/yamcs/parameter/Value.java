package org.yamcs.parameter;

import org.yamcs.xtce.PathElement;

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
    
    /**
     * This function is used to retrieve values from hierarchical aggregates.
     * 
     * It is equivalent with a chain of {@link #getMemberValue(Value, PathElement[]))} calls:
     * 
     * <pre>
     *   getMemberValue(getMemberValue(getMemberValue(value, path[0]),path[1])...,path[n])
     * </pre>
     * 
     * It returns null if the path does not lead to a valid aggregate member.
     * 
     * @param path
     *            - the path to be traversed, can be empty.
     * @return the member value found by traversing the path or null if no such member exists. In case the path is
     *         empty, this value itself will be returned.
     */
    public static Value getMemberValue(Value value, PathElement[] path) {
        Value v = value;
        for (int i = 0; i < path.length; i++) {
            PathElement pe = path[i];
            String name = pe.getName();
            int[] idx = pe.getIndex();
            if (v instanceof AggregateValue) {
                if (name == null) {
                    return null;
                }
                v = ((AggregateValue) v).getMemberValue(name);
            } else if (name != null) {
                return null;
            }

            if (v instanceof ArrayValue) {
                if (idx == null) {
                    return null;
                }
                v = ((ArrayValue) v).getElementValue(idx);
            } else if (idx != null) {
                return null;
            }
            
            if (v == null) {
                return null;
            }

        }
        return v;
    }

}
