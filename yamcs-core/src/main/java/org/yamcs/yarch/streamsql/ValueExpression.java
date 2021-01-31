package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.ConstantValueCompiledExpression;
import org.yamcs.yarch.DataType;

import java.util.List;
import java.util.UUID;

import org.yamcs.time.Instant;
import org.yamcs.utils.parser.ParseException;

/**
 * This represents a constant value coming from some sql expression
 * for example: select 3 from x
 * 
 * @author nm
 *
 */
public class ValueExpression extends Expression {
    ValueExpression(Object value) {
        super(null);
        this.constantValue = value;
    }

    public ValueExpression(Object value, DataType type) {
        super(null);
        this.constantValue = value;
        this.type = type;
    }

    @Override
    public String toString() {
        return constantValue.toString();
    }

    @Override
    public void doBind() throws StreamSqlException {
        type = DataType.typeOf(constantValue);
    }

    ValueExpression getNegative() throws ParseException {
        Object newv;
        if (constantValue instanceof Byte) {
            newv = (byte) -1 * (Byte) constantValue;
        } else if (constantValue instanceof Short) {
            newv = (short) -1 * (Short) constantValue;
        } else if (constantValue instanceof Integer) {
            newv = (int) -1 * (Integer) constantValue;
        } else if (constantValue instanceof Double) {
            newv = (double) -1 * (Double) constantValue;
        } else if (constantValue instanceof Long) {
            newv = (long) -1 * (Long) constantValue;
        } else {
            throw new ParseException("Cannot have a negative value of a " + constantValue.getClass());
        }
        return new ValueExpression(newv);
    }

    @Override
    protected void fillCode_Declarations(StringBuilder code) {
        if (constantValue instanceof UUID) {
            UUID uuid = (UUID) constantValue;
            code.append("\tprivate final ")
                    .append(UUID.class.getName()).append(" const_uuid = ")
                    .append(UUID.class.getName()).append(".fromString(\"")
                    .append(uuid.toString()).append("\");\n");
        } else if (constantValue instanceof Instant) {
            Instant t = (Instant) constantValue;
            code.append("\tprivate final ")
                    .append(Instant.class.getName()).append(" const_instant = ")
                    .append(Instant.class.getName()).append(".get(").append(t.getMillis())
                    .append("l, ").append(t.getPicos()).append(");\n");
        }
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        if ((constantValue instanceof Byte) || (constantValue instanceof Short) || (constantValue instanceof Integer)) {
            code.append(constantValue.toString());
        } else if (constantValue instanceof Long) {
            code.append(constantValue.toString()).append("l");
        } else if (constantValue instanceof String) {
            code.append('"');
            escapeJavaString((String) constantValue, code);
            code.append('"');
        } else if (constantValue instanceof UUID) {
            code.append("const_uuid");
        } else if (constantValue instanceof Instant) {
            code.append("const_instant");
        } else if (constantValue instanceof List<?>) {
            code.append("const_list");
        } else {
            throw new NotImplementedException(constantValue.getClass() + " not usable in constants");
        }
    }

    @Override
    public CompiledExpression compile() {
        ColumnDefinition def = new ColumnDefinition(constantValue.toString(), type);
        return new ConstantValueCompiledExpression(constantValue, def);
    }

    /**
     * Copied (and modified a little) from org.apache.commons.lang
     * 
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
                sb.append("\\u" + Integer.toHexString(ch).toUpperCase());
            } else if (ch > 0xff) {
                sb.append("\\u0" + Integer.toHexString(ch).toUpperCase());
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
                default:
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
                default:
                    sb.append(ch);
                    break;
                }
            }
        }
    }
}
