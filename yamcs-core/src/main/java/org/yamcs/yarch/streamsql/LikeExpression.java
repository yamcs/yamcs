package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.DataType;


public class LikeExpression extends Expression {
    LikeClause likeClause;
    public LikeExpression(Expression expr, LikeClause likeClause) {
        super(new Expression[]{expr});
        this.likeClause=likeClause;
    }

    @Override
    protected void doBind() throws StreamSqlException {
        type = DataType.BOOLEAN;
    }

    @Override
    protected void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        if(likeClause.negation) {
            code.append("!");
        }
        code.append("org.yamcs.yarch.streamsql.Utils.like(");
        children[0].fillCode_getValueReturn(code);
        code.append(", \"");
        code.append(likeClause.pattern);
        code.append("\")");
    }

}
