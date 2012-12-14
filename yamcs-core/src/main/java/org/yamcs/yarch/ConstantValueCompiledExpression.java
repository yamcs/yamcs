package org.yamcs.yarch;

/**
 * this is no compiled expression at all, it just returns the same value
 * @author nm
 *
 */
public class ConstantValueCompiledExpression implements CompiledExpression {
    Object  value;
    ColumnDefinition cdef;
    
    public ConstantValueCompiledExpression(Object value2, ColumnDefinition cdef) {
        this.value=value2;
        this.cdef=cdef;
    }
    @Override
    public Object getValue(Tuple tuple) {
        return value;
    }
    @Override
    public ColumnDefinition getDefinition() {
        return cdef;
    }
}
