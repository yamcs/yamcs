package org.yamcs.yarch;

public class FieldReturnCompiledExpression implements CompiledExpression {
    final String field;
    final ColumnDefinition cdef;
    public FieldReturnCompiledExpression(String field, ColumnDefinition cdef) {
        this.field=field;
        this.cdef=cdef;
    }
    @Override
    public Object getValue(Tuple tuple) {
        return tuple.getColumn(field);
    }
    @Override
    public ColumnDefinition getDefinition() {
        return cdef;
    }
    
    @Override
    public String toString() {
        return "FieldReturnCompiledExpression(field: "+field+" columnDefinition: "+cdef+")";
    }
}
