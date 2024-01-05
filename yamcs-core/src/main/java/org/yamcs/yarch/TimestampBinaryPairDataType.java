package org.yamcs.yarch;

import org.apache.commons.lang3.tuple.Pair;

public class TimestampBinaryPairDataType extends DataType {

    private final Pair<DataType, DataType> pair;

    protected TimestampBinaryPairDataType() {
        super(_type.TIMESTAMP_BINARY_PAIR, DataType.TIMESTAMP_BINARY_PAIR_ID);
        this.pair = Pair.of(DataType.TIMESTAMP, DataType.BINARY);
    }

    public Pair<DataType, DataType> getPair() {
        return pair;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasEnums() {
        return false;
    }

    @Override
    public int hashCode() {
        return pair.hashCode();
    }

    @Override
    public String name() {
        return DataType.TIMESTAMP + "_" + DataType.BINARY + "_" + "PAIR";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        TimestampBinaryPairDataType other = (TimestampBinaryPairDataType) obj;
        if (!pair.equals(other.pair)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return name();
    }

}
