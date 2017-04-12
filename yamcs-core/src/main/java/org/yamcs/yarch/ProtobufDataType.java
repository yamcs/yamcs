package org.yamcs.yarch;


public class ProtobufDataType extends DataType {

private final String className;
    protected ProtobufDataType(String className) {
        super(_type.PROTOBUF);
        this.className = className;
    }
    
    
    public String toString() {
        return name();
    } 
    
    public String name() {
        return "PROTOBUF("+className+")";
    }
    
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result
                + ((className == null) ? 0 : className.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ProtobufDataType other = (ProtobufDataType) obj;
        if (className == null) {
            if (other.className != null)
                return false;
        } else if (!className.equals(other.className))
            return false;
        return true;
    }


    public String getClassName() {
        return className;
    }
}
