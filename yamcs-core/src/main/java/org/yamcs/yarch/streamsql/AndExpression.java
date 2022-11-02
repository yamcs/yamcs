package org.yamcs.yarch.streamsql;

import java.util.List;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.FilterableTarget;
import org.yamcs.utils.parser.ParseException;

public class AndExpression extends Expression {
    public AndExpression(List<Expression> list) throws ParseException {
        super(list.toArray(new Expression[0]));
    }

    @Override
    public void addFilter(FilterableTarget tableStream) throws StreamSqlException {
        for (Expression expr : children) {
            expr.addFilter(tableStream);
        }
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        boolean first = true;
        code.append("SqlExpressions.AND(");
        for (Expression expr : children) {
            if (!first) {
                code.append(", ");
            } else {
                first = false;
            }
            expr.fillCode_getValueReturn(code);
        }
        code.append(")");
    }

    @Override
    public void doBind() throws StreamSqlException {
        for (Expression c : children) {
            if (c.getType() != DataType.BOOLEAN) {
                throw new GenericStreamSqlException("'" + c + "' is not of type boolean");
            }
        }
        type = DataType.BOOLEAN;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        boolean first = true;
        for (Expression expr : children) {
            if (first) {
                first = false;
            } else {
                sb.append(" AND ");
            }
            sb.append("(");
            sb.append(expr.toString());
            sb.append(")");
        }
        return sb.toString();
    }
}
