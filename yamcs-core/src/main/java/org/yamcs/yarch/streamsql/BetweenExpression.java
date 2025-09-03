package org.yamcs.yarch.streamsql;

import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.FilterableTarget;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

import org.yamcs.utils.parser.ParseException;

public class BetweenExpression extends Expression {
    static AtomicInteger at = new AtomicInteger();
    boolean negation;
    
    public BetweenExpression(Expression expr, BetweenClause betweenClause) throws ParseException {
        super(new Expression[] { expr, betweenClause.expr1, betweenClause.expr2 });
        this.negation = betweenClause.negation;
    }

    @Override
    public void addFilter(FilterableTarget tableStream) throws StreamSqlException {
        if (!(children[0] instanceof ColumnExpression)) {
            return;
        }

        // For BETWEEN, we need both bounds to be constant for filter optimization
        if (!children[1].isConstant() || !children[2].isConstant()) {
            return;
        }

        ColumnExpression cexpr = (ColumnExpression) children[0];
        Object lowerBound = children[1].getConstantValue();
        Object upperBound = children[2].getConstantValue();
        
        if (!negation) {
            // x BETWEEN a AND b  =>  x >= a AND x <= b
            tableStream.addRelOpFilter(cexpr, RelOp.GREATER_OR_EQUAL, lowerBound);
            tableStream.addRelOpFilter(cexpr, RelOp.LESS_OR_EQUAL, upperBound);
        } else {
            // x NOT BETWEEN a AND b  =>  x < a OR x > b
            // We can't represent OR with multiple filters, so skip optimization for NOT BETWEEN
            return;
        }
    }

    @Override
    protected void doBind() throws StreamSqlException {
        DataType exprType = children[0].getType();
        DataType lowerType = children[1].getType();
        DataType upperType = children[2].getType();
        
        // Check/convert lower bound
        if (!DataType.compatible(lowerType, exprType)) {
            // If lower bound is constant, attempt automatic conversion
            if (children[1].isConstant()) {
                try {
                    Object v = DataType.castAs(lowerType, exprType, children[1].getConstantValue());
                    children[1] = new ValueExpression(v, exprType);
                } catch (IllegalArgumentException e) {
                    throw new StreamSqlException(ErrCode.INCOMPATIBLE,
                            "Cannot convert " + children[1].getConstantValue() + " to " + exprType);
                }
            } else {
                throw new IncompatibilityException(children[1] + " is of different type than " + children[0] + "("
                        + lowerType + " versus " + exprType + ")");
            }
        }
        
        // Check/convert upper bound
        if (!DataType.compatible(upperType, exprType)) {
            // If upper bound is constant, attempt automatic conversion  
            if (children[2].isConstant()) {
                try {
                    Object v = DataType.castAs(upperType, exprType, children[2].getConstantValue());
                    children[2] = new ValueExpression(v, exprType);
                } catch (IllegalArgumentException e) {
                    throw new StreamSqlException(ErrCode.INCOMPATIBLE,
                            "Cannot convert " + children[2].getConstantValue() + " to " + exprType);
                }
            } else {
                throw new IncompatibilityException(children[2] + " is of different type than " + children[0] + "("
                        + upperType + " versus " + exprType + ")");
            }
        }
        
        type = DataType.BOOLEAN;
    }

    @Override
    protected void fillCode_getValueBody(StringBuilder code) throws StreamSqlException {
        // No need to calculate bounds at runtime - they're handled directly in getValueReturn
        // Non-constant expressions will be evaluated when fillCode_getValueReturn is called
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        if(negation) {
            code.append("!");
        }
        // Generate: (value >= lowerBound && value <= upperBound)
        code.append("(org.yamcs.yarch.DataType.compare(");
        children[0].fillCode_getValueReturn(code);
        code.append(", ");
        children[1].fillCode_getValueReturn(code);  // This generates the ValueExpression field name
        code.append(") >= 0 && org.yamcs.yarch.DataType.compare(");
        children[0].fillCode_getValueReturn(code);
        code.append(", ");
        children[2].fillCode_getValueReturn(code);  // This generates the ValueExpression field name
        code.append(") <= 0)");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(children[0]);
        if(negation) {
            sb.append(" NOT");
        }
        sb.append(" BETWEEN ");
        sb.append(children[1].toString());
        sb.append(" AND ");
        sb.append(children[2].toString());
        return sb.toString();
    }

}
