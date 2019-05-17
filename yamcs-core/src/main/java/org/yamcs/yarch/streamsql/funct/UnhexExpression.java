package org.yamcs.yarch.streamsql.funct;

import java.util.Arrays;

import org.yamcs.utils.StringConverter;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

public class UnhexExpression extends Expression {

    public UnhexExpression(Expression[] args, boolean star) throws ParseException {
        super(args);

    }

    @Override
    protected void doBind() throws StreamSqlException {
        if (children.length != 1) {
            throw new StreamSqlException(ErrCode.WRONG_ARG_COUNT, "Invalid number of arguments, expected 1 (byte[])");
        }
       Expression ch0 = children[0];
        if (ch0.getType() != DataType.STRING) {
            throw new StreamSqlException(ErrCode.BAD_ARG_TYPE, "expected string");
        }
        if(ch0.isConstant()) {
            this.constantValue = StringConverter.hexStringToArray((String)ch0.getConstantValue());
        }
        type = DataType.BINARY;
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        if(constantValue!=null) {
            code.append("const_"+getColumnName());
        } else {
            code.append(" org.yamcs.utils.StringConverter.hexStringToArray(");
            children[0].fillCode_getValueReturn(code);
            code.append(")");
        }
    }

    static public byte[] substring(byte[] b, int offset) {
        return Arrays.copyOfRange(b, offset, b.length);
    }

}
