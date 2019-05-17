package org.yamcs.yarch.streamsql.funct;

import java.util.Arrays;

import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class SubstringExpression extends Expression {

    public SubstringExpression(Expression[] args, boolean star) throws ParseException {
        super(args);
        if(args.length!=2 && args.length!=3) {
            throw new ParseException("Invalid number of arguments, expected 2 (byte[], offset) or 3 (byte[], offset, length)");
        }
    }

    @Override
    protected void doBind() throws StreamSqlException {
        type = children[0].getType();
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        code.append("org.yamcs.yarch.streamsql.funct.SubstringExpression.substring(");
        children[0].fillCode_getValueReturn(code);
        code.append(",");
        children[1].fillCode_getValueReturn(code);
        
        code.append(")");
    }
    
    static public byte[] substring(byte[] b, int offset) {
        return  Arrays.copyOfRange(b, offset, b.length);
    }
    
}
