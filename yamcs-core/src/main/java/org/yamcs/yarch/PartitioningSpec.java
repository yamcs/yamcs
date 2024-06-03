package org.yamcs.yarch;

public class PartitioningSpec {
    public enum _type {
        NONE, // no partition at all
        TIME, // partition by time
        VALUE, // partition by value
        TIME_AND_VALUE // partition by time and value (in this order)
    }

    final public _type type;
    final public String timeColumn;
    final public String valueColumn;
    final TimePartitionSchema timePartitioningSchema;

    // this thing is not final because it is determined the TableDefinition when attaching the pspec. Could be changed
    // into a builder pattern.
    private DataType valueColumnType;

    PartitioningSpec(_type type, String timeColumn, String valueColumn, String timePartitioningSchema) {
        this.type = type;
        this.timeColumn = timeColumn;
        this.valueColumn = valueColumn;

        this.timePartitioningSchema = (timePartitioningSchema == null)? 
                TimePartitionSchema.getInstance("YYYY") :
                TimePartitionSchema.getInstance(timePartitioningSchema);
    }

    public static PartitioningSpec noneSpec() {
        return new PartitioningSpec(_type.NONE, null, null, null);
    }

    public static PartitioningSpec valueSpec(String valueColumn) {
        return new PartitioningSpec(_type.VALUE, null, valueColumn, null);
    }

    public static PartitioningSpec timeSpec(String timeColumn, String timePartitioningSchema) {
        return new PartitioningSpec(_type.TIME, timeColumn, null, timePartitioningSchema);
    }

    public static PartitioningSpec timeAndValueSpec(String timeColumn, String valueColumn,
            String timePartitioningSchema) {
        return new PartitioningSpec(_type.TIME_AND_VALUE, timeColumn, valueColumn, timePartitioningSchema);
    }

    public DataType getValueColumnType() {
        return valueColumnType;
    }

    public void setValueColumnType(DataType valueColumnType) {
        if (type != _type.VALUE && type != _type.TIME_AND_VALUE) {
            throw new IllegalArgumentException("value column type not allowed for type " + type);
        }
        this.valueColumnType = valueColumnType;
    }

    public TimePartitionSchema getTimePartitioningSchema() {
        return timePartitioningSchema;
    }

    @Override
    public String toString() {
        return "timeColumn: " + timeColumn + " valueColumn:" + valueColumn;
    }
}
