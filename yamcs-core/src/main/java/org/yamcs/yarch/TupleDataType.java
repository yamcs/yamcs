package org.yamcs.yarch;


public class TupleDataType extends DataType {
    private final TupleDefinition td;
    protected TupleDataType(TupleDefinition td) {
        super(_type.TUPLE, TUPLE_ID);
        this.td = td;
    }

    public String toString() {
        return name();
    }

    public String name() {
        return "TUPLE("+td.toString()+")";
    }

}
