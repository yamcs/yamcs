package org.yamcs.xtce;

public abstract class NumericDataType extends BaseDataType {
    private static final long serialVersionUID = 5L;
    
    protected NumericDataType(BaseDataType.Builder<?> builder) {
        super(builder);
    }
    
    NumericDataType(String name) {
        super(name);
    }
    protected NumericDataType(NumericDataType t) {
        super(t);
    }
    
    
    public abstract static class Builder<T extends Builder<T>> extends BaseDataType.Builder<T> {
        
    }
    
}
