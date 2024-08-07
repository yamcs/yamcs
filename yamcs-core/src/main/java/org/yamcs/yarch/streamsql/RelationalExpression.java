package org.yamcs.yarch.streamsql;

import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.ArrayDataType;
import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.FilterableTarget;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

/**
 * Expressions of type <code>x &gt; y</code> or <code>x=y</code>
 * 
 * @author nm
 *
 */
public class RelationalExpression extends Expression {
    RelOp relOp;

    public RelationalExpression(Expression left, Expression right, RelOp relOp) throws ParseException {
        super(new Expression[] { left, right });
        this.relOp = relOp;
        // if (left.isConstant() && right.isConstant())
        // constant = true;
    }

    public RelOp getRelation() {
        return relOp;
    }

    @Override
    public void addFilter(FilterableTarget tableStream) throws StreamSqlException {
        if ((children[1] instanceof ColumnExpression) && (children[0].isConstant())) {
            // swap left with right
            Expression tmp = children[1];
            children[1] = children[0];
            children[0] = tmp;
            relOp = relOp.getOppsite();
        }

        if ((children[0] instanceof ColumnExpression) && children[1].isConstant()) {
            ColumnExpression cexpr = (ColumnExpression) children[0];
            Object cvalue;
            if (children[1] instanceof ValueExpression) {
                cvalue = children[1].getConstantValue();
            } else {
                CompiledExpression compexpr = children[1].compile();
                cvalue = compexpr.getValue(null);
            }

            tableStream.addRelOpFilter(cexpr, relOp, cvalue);
        }
    }

    @Override
    public void doBind() throws StreamSqlException {
        type = DataType.BOOLEAN;

        DataType ltype = children[0].getType();
        DataType rtype = children[1].getType();

        if (relOp == RelOp.OVERLAP) {
            if (!(ltype instanceof ArrayDataType) || !(rtype instanceof ArrayDataType)) {
                throw new StreamSqlException(ErrCode.INCOMPATIBLE,
                        "Overlap operator " + relOp.getSign() + " can only be used between two arrays");
            }
        }
        if (DataType.compatible(ltype, rtype)) {
            return;
        }

        // if any of the two children is constant, we attempt a conversion, otherwise we throw an exception (an explicit
        // conversion should be used)
        if (children[0].isConstant()) {
            try {
                Object v = DataType.castAs(ltype, rtype, children[0].getConstantValue());
                children[0] = new ValueExpression(v, rtype);
                return;
            } catch (IllegalArgumentException e) {
                throw new StreamSqlException(ErrCode.INCOMPATIBLE,
                        "Cannot convert " + children[0].getConstantValue() + " to " + rtype);
            }
        } else if (children[1].isConstant()) {
            try {
                Object v = DataType.castAs(rtype, ltype, children[1].getConstantValue());
                children[1] = new ValueExpression(v, ltype);
                return;
            } catch (IllegalArgumentException e) {
                throw new StreamSqlException(ErrCode.INCOMPATIBLE,
                        "Cannot convert " + children[1].getConstantValue() + " to " + ltype);
            }
        }

        throw new StreamSqlException(ErrCode.INCOMPATIBLE, "Cannot compare " + ltype + " and " + rtype);

    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        DataType ch0dt = children[0].getType();
        if (ch0dt.isComparable()) {
            code.append("SqlExpressions." + relOp.name() + "(");
            children[0].fillCode_getValueReturn(code);
            code.append(", ");
            children[1].fillCode_getValueReturn(code);
            code.append(") ");
        } else {
            switch (relOp) {
            case NOT_EQUAL:
                code.append("!");
            case EQUAL: // intentional fall through
                code.append("Objects.equals(");
                children[0].fillCode_getValueReturn(code);
                code.append(",");
                children[1].fillCode_getValueReturn(code);
                code.append(")");
                break;
            case OVERLAP:
                code.append("SqlArrays.overlap(");
                children[0].fillCode_getValueReturn(code);
                code.append(", ");
                children[1].fillCode_getValueReturn(code);
                code.append(")");
                break;
            default:
                throw new StreamSqlException(ErrCode.COMPILE_ERROR,
                        "Cannot use " + relOp + " not supported for data type " + ch0dt);
            }
        }
    }

    @Override
    public String toString() {
        return children[0] + " " + relOp.getSign() + " " + children[1];
    }
}
