package org.yamcs.yarch;

public class ListDataType extends DataType {
    private final TupleDefinition td;
    protected ListDataType(TupleDefinition td) {
        super(_type.TUPLE);
        this.td = td;
    }

}
