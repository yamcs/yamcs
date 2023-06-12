package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;

/**
 * This corresponds to an ? argument passed to a query
 *
 */
public class ArgumentExpression extends Expression implements CompiledExpression {
    final int n;

    public ArgumentExpression(int n, Object value) {
        super(null);
        this.n = n;
        this.constantValue = value;
    }

    @Override
    protected void doBind() throws StreamSqlException {
        if (constantValue != null) {
            type = DataType.typeOf(constantValue);
        }
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        if (constantValue != null) {
            code.append("(" + type.javaType() + ")__sql_args[" + n + "]");
        } else {
            code.append("__sql_args[" + n + "]");
        }
    }

    @Override
    public ColumnDefinition getDefinition() {
        return null;
    }

    @Override
    public Object getValue(Tuple tuple) {
        return constantValue;
    }
}
