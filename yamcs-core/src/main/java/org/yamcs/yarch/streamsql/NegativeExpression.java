package org.yamcs.yarch.streamsql;

import org.yamcs.utils.parser.ParseException;

public class NegativeExpression extends Expression {

    public NegativeExpression(Expression expr) throws ParseException {
        super(new Expression[] { expr });
    //    constant = expr.isConstant();
    }

    @Override
    public void doBind() throws StreamSqlException {
        type = children[0].getType();
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        code.append("-");
        children[0].fillCode_getValueReturn(code);
    }
}
