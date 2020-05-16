package org.yamcs.yarch;

public class ListDataType extends DataType {
    private final TupleDefinition td;
    protected ListDataType(TupleDefinition td) {
        super(_type.LIST, LIST_ID);
        this.td = td;
    }
    public TupleDefinition getTd() {
        return td;
    }
}
