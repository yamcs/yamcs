package org.yamcs.yarch.streamsql.funct;

import org.yamcs.yarch.streamsql.Expression;

import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class CoalesceExpression extends Expression {
    public CoalesceExpression(Expression[] args, boolean star) throws ParseException {
        super(args);
    }

    @Override
    protected void doBind() throws StreamSqlException {
        type = children[0].getType();
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        if (constantValue != null) {
            code.append("const_" + getColumnName());
        } else {
            code.append(" org.yamcs.yarch.streamsql.funct.CoalesceExpression.coalesce(");
            for (int i = 0; i < children.length; i++) {
                if (i != 0) {
                    code.append(", ");
                }
                children[i].fillCode_getValueReturn(code);
            }
            code.append(")");
        }
    }

    public static Object coalesce(Object... objs) {
        for (Object o : objs) {
            if (o != null) {
                return o;
            }
        }
        return null;
    }

}
