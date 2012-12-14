package org.yamcs.yarch;

import org.yamcs.yarch.Tuple;

public interface CompiledExpression {
    ColumnDefinition getDefinition();
    Object getValue(Tuple tuple);  

}