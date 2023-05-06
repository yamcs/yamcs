package org.yamcs.yarch.streamsql;

/**
 * This represents a NULL value coming from some sql expression for example: select null from x
 */
public class NullExpression extends Expression {

    NullExpression() {
        super(null);
        this.constantValue = null;
    }

    @Override
    public void doBind() throws StreamSqlException {
        // No type
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        code.append("null");
    }
}
