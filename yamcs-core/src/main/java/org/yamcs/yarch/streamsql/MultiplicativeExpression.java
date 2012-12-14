package org.yamcs.yarch.streamsql;

import java.util.List;

import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.MultOp;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;



public class MultiplicativeExpression extends Expression {
    List<MultOp> ops;
    public MultiplicativeExpression(List<Expression> exprs, List<MultOp> ops) throws ParseException {
        super(exprs.toArray(new Expression[0]));
        this.ops=ops;
    }

    @Override
    public void doBind() throws StreamSqlException {
        // TODO Auto-generated method stub

    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        // TODO Auto-generated method stub

    }
}
