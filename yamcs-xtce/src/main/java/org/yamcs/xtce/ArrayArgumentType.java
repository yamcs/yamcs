package org.yamcs.xtce;

import java.util.List;

/**
 * Describe an array parameter type.
 * <p>
 * The size and number of dimensions are described here. See ArrayParameterRefEntryType, NameReferenceType and
 * ArrayDataType.
 *
 */
public class ArrayArgumentType extends ArrayDataType implements ArgumentType {
    private static final long serialVersionUID = 2L;

    ArrayArgumentType(Builder builder) {
        super(builder);
    }

    public ArrayArgumentType(String name, int numberOfDimensions) {
        super(name, numberOfDimensions);
    }

    public ArrayArgumentType(String name) {
        super(name, -1);
    }

    public ArrayArgumentType(ArrayArgumentType t) {
        super(t);
    }

    @Override
    public List<UnitType> getUnitSet() {
        return null;
    }

    @Override
    public ArrayArgumentType.Builder toBuilder() {
        return new Builder(this);
    }

    public static class Builder extends ArrayDataType.Builder<Builder> implements ArgumentType.Builder<Builder> {
        public Builder() {
        }

        public Builder(ArrayArgumentType arrayArgumentType) {
            super(arrayArgumentType);
        }

        @Override
        public ArrayArgumentType build() {
            return new ArrayArgumentType(this);
        }

        @Override
        public Builder setEncoding(DataEncoding.Builder<?> dataEncoding) {
            throw new UnsupportedOperationException("array arguments do not support encodings");
        }

        @Override
        public org.yamcs.xtce.DataEncoding.Builder<?> getEncoding() {
            throw new UnsupportedOperationException("array arguments do not support encodings");
        }
    }
}
