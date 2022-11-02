package org.yamcs.yarch.streamsql;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.yamcs.yarch.DataType;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

public class AdditiveExpression extends Expression {
    List<AddOp> ops;

    public AdditiveExpression(List<Expression> exprs, List<AddOp> ops) throws ParseException {
        super(exprs.toArray(new Expression[0]));
        this.ops = ops;
    }

    @Override
    public void doBind() throws StreamSqlException {
        boolean constant = Arrays.stream(children).allMatch(e -> e.isConstant());
        if (constant) {
            computeConstantValue();
        } else {
            type = children[0].getType();
            if(DataType.isNumber(type)) {
                Optional<Expression> o = Arrays.stream(children).filter(c->!DataType.isNumber(c.getType())).findAny();
                if(o.isPresent()) {
                    throw new StreamSqlException(ErrCode.BAD_ARG_TYPE, "Cannot add numeric with data type " + o.get().getType());
                }
            } else {
                if (ops.contains(AddOp.MINUS)) {
                    throw new StreamSqlException(ErrCode.BAD_ARG_TYPE, "Cannot use minus on data type " + type);
                }
            }
        }
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        if (DataType.isNumber(type) || type==DataType.STRING) {
            code.append("(");
            children[0].fillCode_getValueReturn(code);
            for (int i = 0; i < ops.size(); i++) {
                code.append(ops.get(i).getSign());
                children[i + 1].fillCode_getValueReturn(code);
            }
            code.append(")");
        } else {
            code.append("org.yamcs.yarch.streamsql.AdditiveExpression._binaryConcat(");
            children[0].fillCode_getValueReturn(code);
            for (int i = 0; i < ops.size(); i++) {
                code.append(",");
                children[i + 1].fillCode_getValueReturn(code);
            }
            code.append(")");
        }
    }
    

    private void computeConstantValue() throws StreamSqlException {
        DataType ch0t = children[0].getType();
        if (ch0t == DataType.STRING) {
            constantValue = concatStringChildren();
        } else if (ch0t == DataType.BINARY) {
            constantValue = concatBinaryChildren();
        } else if (DataType.isNumber(ch0t)) {
            BigDecimal x = addNumbChildren();
            if (x.stripTrailingZeros().scale() <= 0) {
                long lx = x.longValue();
                if (lx == (int) lx) {
                    constantValue = (int) lx;
                    type = DataType.LONG;
                } else {
                    constantValue = lx;
                    type = DataType.INT;
                }
            }
        } else {
            throw new StreamSqlException(ErrCode.BAD_ARG_TYPE, "Cannot use additive expression for " + ch0t);
        }

    }

    private String concatStringChildren() throws StreamSqlException {
        StringBuilder sb = new StringBuilder();
        for (Expression c : children) {
            if (c.getType() != DataType.STRING) {
                throw new StreamSqlException(ErrCode.BAD_ARG_TYPE, "Cannot add String to " + c.getType());
            }
            sb.append((String) c.getConstantValue());
        }
        return sb.toString();
    }

    private BigDecimal addNumbChildren() throws StreamSqlException {
        BigDecimal s = new BigDecimal(0);
        for (Expression c : children) {
            if (!DataType.isNumber(c.getType())) {
                throw new StreamSqlException(ErrCode.BAD_ARG_TYPE, "Cannot add number to " + c.getType());
            }
            Number x = (Number) c.getConstantValue();
            s = s.add(new BigDecimal(x.toString()));
        }
        return s;
    }

    private byte[] concatBinaryChildren() throws StreamSqlException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (Expression c : children) {
            if (c.getType() != DataType.BINARY) {
                throw new StreamSqlException(ErrCode.BAD_ARG_TYPE, "Cannot add Binary to " + c.getType());
            }
            try {
                baos.write((byte[]) c.getConstantValue());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return baos.toByteArray();
   }
    
    
   public static byte[] binaryConcat(byte[] arg1, byte[] arg2) {
       return _binaryConcat(arg1, arg2);
   }
   public static byte[] binaryConcat(byte[] arg1, byte[] arg2, byte[] arg3) {
       return _binaryConcat(arg1, arg2, arg3);
   }
   
   public static byte[] _binaryConcat(byte[]...args) {
       int length = 0;
       for(byte[] arg: args) {
           length+=arg.length;
       }
       byte[] r = new byte[length];
       int offset = 0;
           
       for(byte[] arg: args) {
           System.arraycopy(arg, 0, r, offset, arg.length);
           offset+=arg.length;
       }
       return r;
   }
}
