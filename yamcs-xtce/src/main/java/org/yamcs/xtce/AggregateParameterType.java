package org.yamcs.xtce;

/**
 * AggegateParameters are analogous to a C struct, they are an aggregation of related data items. Each of these data
 * items is defined here as a 'Member'
 * 
 * @author nm
 *
 */
public class AggregateParameterType extends AggregateDataType implements ParameterType {

    public AggregateParameterType(Builder builder) {
        super(builder);
    }

    public AggregateParameterType(AggregateParameterType t) {
        super(t);
    }

    private static final long serialVersionUID = 1L;

    @Override
    public boolean hasAlarm() {
        return false;
    }

    @Override
    public Builder toBuilder() {
        return new Builder(this);
    }

    @Override
    public DataEncoding getEncoding() {
        throw new UnsupportedOperationException("aggregate parameters do not support encodings");
    }

    public static class Builder extends AggregateDataType.Builder<Builder> implements ParameterType.Builder<Builder> {

        public Builder() {
        }

        public Builder(AggregateParameterType aggregateParameterType) {
            super(aggregateParameterType);
        }

        @Override
        public AggregateParameterType build() {
            return new AggregateParameterType(this);
        }

        @Override
        public Builder setEncoding(DataEncoding.Builder<?> dataEncoding) {
            throw new UnsupportedOperationException("aggregate parameters do not support encodings");
        }

    }
}
