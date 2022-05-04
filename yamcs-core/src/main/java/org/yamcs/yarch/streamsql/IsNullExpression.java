package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.DataType;

public class IsNullExpression extends Expression {
    IsNullClause isNullClause;

    public IsNullExpression(Expression expr, IsNullClause isNullClause) {
        super(new Expression[] { expr });
        this.isNullClause = isNullClause;
    }

    @Override
    protected void doBind() throws StreamSqlException {
        type = DataType.BOOLEAN;
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {

        children[0].fillCode_getValueReturn(code);
        if (isNullClause.negation) {
            code.append("!=");
        } else {
            code.append("==");
        }
        code.append("null");
    }

}
