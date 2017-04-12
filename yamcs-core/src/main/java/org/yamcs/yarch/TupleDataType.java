package org.yamcs.yarch;

import org.yamcs.yarch.DataType._type;

public class TupleDataType extends DataType {
    private final TupleDefinition td;
    protected TupleDataType(TupleDefinition td) {
        super(_type.TUPLE);
        this.td = td;
    }

    public String toString() {
        return name();
    }

    public String name() {
        return "TUPLE("+td.toString()+")";
    }

}
