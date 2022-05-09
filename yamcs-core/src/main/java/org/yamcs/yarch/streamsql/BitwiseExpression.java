package org.yamcs.yarch.streamsql;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.yamcs.yarch.DataType;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

public class BitwiseExpression extends Expression {
    List<BitwiseOp> ops;

    public BitwiseExpression(List<Expression> exprs, List<BitwiseOp> ops) throws ParseException {
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
            Optional<Expression> o = Arrays.stream(children).filter(c -> !DataType.isNumber(c.getType())).findAny();
            if (o.isPresent()) {
                throw new StreamSqlException(ErrCode.BAD_ARG_TYPE,
                        "Cannot use bitwise operators with data type " + o.get().getType());
            }
        }
    }


    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        code.append("(");
        code.append("(" + type.primitiveJavaType() + ")");
        children[0].fillCode_getValueReturn(code);
        for (int i = 0; i < ops.size(); i++) {
            code.append(ops.get(i).getSign());
            code.append("(" + type.primitiveJavaType() + ")");
            children[i + 1].fillCode_getValueReturn(code);
        }
        code.append(")");
    }

    private void computeConstantValue() throws StreamSqlException {
        DataType ch0t = children[0].getType();
        if (DataType.isNumber(ch0t)) {
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
            throw new StreamSqlException(ErrCode.BAD_ARG_TYPE, "Cannot use bitwise expression for " + ch0t);
        }

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
}
