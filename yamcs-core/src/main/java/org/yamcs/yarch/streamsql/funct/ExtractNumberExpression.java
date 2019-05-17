package org.yamcs.yarch.streamsql.funct;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class ExtractNumberExpression extends Expression {
    int numBytes;
    boolean littleEndian;
    public ExtractNumberExpression(Expression[] children, boolean star, int numBytes, boolean littleEndian) {
        super(children);
        this.numBytes = numBytes;
        this.littleEndian = littleEndian;
    }

    @Override
    protected void doBind() throws StreamSqlException {
        type = DataType.INT;
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {

        String c0name = children[0].getColumnName();
        code.append("org.yamcs.utils.ByteArrayUtils.");
        if(numBytes==2) {
            code.append("decodeShort");
        } else if(numBytes==3) {
            code.append("decode3Bytes");
        } else if(numBytes==4) {
            code.append("decodeInt");
        }
        if(littleEndian) {
            code.append("Le");
        }
        code.append("(");
        code.append("col").append(c0name)
            .append(", ");
        children[1].fillCode_getValueReturn(code);
        code.append(")");
    }

}
