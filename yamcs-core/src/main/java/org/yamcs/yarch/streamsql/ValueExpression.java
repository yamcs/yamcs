package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.ConstantValueCompiledExpression;
import org.yamcs.yarch.DataType;

import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.NotImplementedException;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

/**
 * This represents a constant value coming from some sql expression
 *  for example: select 3 from x
 * @author nm
 *
 */
public class ValueExpression extends Expression {
    Object value;

    ValueExpression(Object value) {
        super(null);
        this.value=value;
        constant=true;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public void doBind() throws StreamSqlException {
        type=DataType.typeOf(value);
    }

    ValueExpression getNegative() throws ParseException {
        Object newv;
        if(value instanceof Byte) {
            newv = (byte)-1*(Byte)value;
        } else if(value instanceof Short) {
            newv = (short)-1*(Short)value;
        } else if(value instanceof Integer) {
            newv = (int)-1*(Integer)value;
        } else if(value instanceof Double) {
            newv = (double)-1*(Double)value;
        } else if(value instanceof Long) {
            newv = (long)-1*(Long)value;
        } else {
            throw new ParseException("Cannot have a negative value of a "+value.getClass());
        }
        return new ValueExpression(newv);
    }
    
    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        if((value instanceof Byte) || (value instanceof Short) || (value instanceof Integer)) {
            code.append(value.toString());
        } else if(value instanceof Long) {
            code.append(value.toString()).append("l");
        } else if(value instanceof String) {
            code.append('"');
            escapeJavaString((String)value, code);
            code.append('"');
        } else {
            throw new NotImplementedException(value.getClass()+" not usable in constants");
        }
    }

    @Override
    public CompiledExpression compile() {
        ColumnDefinition def=new ColumnDefinition(value.toString(), type);
        return new ConstantValueCompiledExpression(value, def);
    }
    
    /**
     * Copied (and modified a little) from org.apache.commons.lang
     * @param s
     * @return
     */
    private void escapeJavaString(String str, StringBuilder sb) {
        int sz;
        sz = str.length();
        for (int i = 0; i < sz; i++) {
            char ch = str.charAt(i);

            // handle unicode
            if (ch > 0xfff) {
                sb.append("\\u" +  Integer.toHexString(ch).toUpperCase());
            } else if (ch > 0xff) {
                sb.append("\\u0" +  Integer.toHexString(ch).toUpperCase());
            } else if (ch > 0x7f) {
                sb.append("\\u00" + Integer.toHexString(ch).toUpperCase());
            } else if (ch < 32) {
                switch (ch) {
                case '\b':
                    sb.append('\\');
                    sb.append('b');
                    break;
                case '\n':
                    sb.append('\\');
                    sb.append('n');
                    break;
                case '\t':
                    sb.append('\\');
                    sb.append('t');
                    break;
                case '\f':
                    sb.append('\\');
                    sb.append('f');
                    break;
                case '\r':
                    sb.append('\\');
                    sb.append('r');
                    break;
                default :
                    if (ch > 0xf) {
                        sb.append("\\u00" + Integer.toHexString(ch).toUpperCase());
                    } else {
                        sb.append("\\u000" + Integer.toHexString(ch).toUpperCase());
                    }
                    break;
                }
            } else {
                switch (ch) {
                case '"':
                    sb.append('\\');
                    sb.append('"');
                    break;
                case '\\':
                    sb.append('\\');
                    sb.append('\\');
                    break;
                default :
                    sb.append(ch);
                    break;
                }
            } 
        }
    }
}
