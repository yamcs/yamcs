package org.yamcs.yarch;

public interface CompiledExpression {
    ColumnDefinition getDefinition();
    Object getValue(Tuple tuple);  

}