package org.yamcs.yarch.streamsql;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.FilterableTarget;

import org.yamcs.utils.parser.ParseException;

public class InExpression extends Expression {
    static AtomicInteger at = new AtomicInteger();
    private int count = at.incrementAndGet();
    boolean negation;
    
    public InExpression(Expression expr, InClause inClause) throws ParseException {
        super(getChildren(expr, inClause.list));
        this.negation = inClause.negation;
    }

    private static Expression[] getChildren(Expression expr, List<Expression> list) {
        Expression[] c = new Expression[list.size() + 1];
        c[0] = expr;
        int i = 1;
        for (Expression e : list)
            c[i++] = e;
        return c;
    }

    @Override
    public void addFilter(FilterableTarget tableStream) throws StreamSqlException {
        if (!(children[0] instanceof ColumnExpression)) {
            return;
        }

        for (int i = 1; i < children.length; i++) {
            if (!children[1].isConstant())
                return;
        }

        ColumnExpression cexpr = (ColumnExpression) children[0];
        Set<Object> values = new HashSet<Object>();
        for (int i = 1; i < children.length; i++) {
            Object cvalue;
            if (children[i].isConstant()) {
                cvalue = children[i].getConstantValue();
            } else {
                CompiledExpression compexpr = children[i].compile();
                cvalue = compexpr.getValue(null);
            }
            values.add(cvalue);
        }
        tableStream.addInFilter(cexpr,  negation, values);
    }

    @Override
    protected void doBind() throws StreamSqlException {
        for (int i = 1; i < children.length; i++) {
            if (!DataType.compatible(children[i].getType(), children[0].getType())) {
                throw new IncompatibilityException(children[i] + " is of different type than " + children[0] + "("
                        + children[i].getType() + " versus " + children[0].getType() + ")");
            }
        }
        type = DataType.BOOLEAN;
    }

    @Override
    protected void fillCode_Declarations(StringBuilder code) {
        code.append("\tjava.util.Set inSet" + count + "=new java.util.HashSet();\n");
    }

    @Override
    protected void fillCode_Constructor(StringBuilder code) throws StreamSqlException {
        for (int i = 1; i < children.length; i++) {
            if (children[i].isConstant()) {
                code.append("\t\tinSet" + count + ".add(");
                children[i].fillCode_getValueReturn(code);
                code.append(");\n");
            }
        }
    }

    @Override
    protected void fillCode_getValueBody(StringBuilder code) throws StreamSqlException {
        for (int i = 1; i < children.length; i++) {
            if (!children[i].isConstant()) {
                code.append("\t\tinSet" + count + ".add(");
                children[i].fillCode_getValueReturn(code);
                code.append(");\n");
            }
        }
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        if(negation) {
            code.append("!");
        }
        code.append("inSet" + count + ".contains(");
        children[0].fillCode_getValueReturn(code);
        code.append(")");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(children[0]);
        if(negation) {
            sb.append(" NOT");
        }
        sb.append(" IN (");
        boolean first = true;
        for (int i = 1; i < children.length; i++) {
            if (!first)
                sb.append(", ");
            else
                first = false;
            sb.append(children[i].toString());
        }
        sb.append(")");
        return sb.toString();
    }

}
