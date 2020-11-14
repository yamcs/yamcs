package org.yamcs.yarch.streamsql.funct;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class ExtractNumberExpression extends Expression {
    String fname;
    public ExtractNumberExpression(Expression[] children, boolean star, String fname) {
        super(children);
        this.fname = fname;        
    }

    @Override
    protected void doBind() throws StreamSqlException {
        type = DataType.INT;
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {

        String c0name = children[0].getColumnName();
        code.append("org.yamcs.utils.ByteArrayUtils.");
        code.append(fname);
        code.append("(");
        code.append("col").append(c0name)
            .append(", ");
        children[1].fillCode_getValueReturn(code);
        code.append(")");
    }
}
