package org.yamcs.xtce;

import java.util.List;

import org.yamcs.xtce.BooleanArgumentType.Builder;

public class AggregateArgumentType extends AggregateDataType implements ArgumentType {
    private static final long serialVersionUID = 2L;
    
    public AggregateArgumentType(Builder builder) {
        super(builder);
    }
    
    public AggregateArgumentType(String name) {
        super(name);
    }

    public AggregateArgumentType(AggregateArgumentType t) {
        super(t);
    }


    @Override
    public List<UnitType> getUnitSet() {
        return null;
    }

    @Override
    public AggregateArgumentType.Builder toBuilder() {
        return new Builder(this);
    }
    
    public static class Builder extends AggregateDataType.Builder<Builder> implements ArgumentType.Builder<Builder>{
        public Builder() {
        }
        
        public Builder(AggregateArgumentType aggregateArgumentType) {
            super(aggregateArgumentType);
        }
        @Override
        public AggregateArgumentType build() {
            return new AggregateArgumentType(this);
        }
        @Override
        public Builder setEncoding(DataEncoding.Builder<?> dataEncoding) {
            throw new UnsupportedOperationException("aggregate arguments do not support encodings");
        }
    }
}
