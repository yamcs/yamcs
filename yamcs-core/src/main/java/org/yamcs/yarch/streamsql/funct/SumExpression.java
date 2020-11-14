package org.yamcs.yarch.streamsql.funct;

import org.yamcs.yarch.streamsql.CompilableAggregateExpression;
import org.yamcs.yarch.streamsql.Expression;

import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class SumExpression extends CompilableAggregateExpression {

    public SumExpression(Expression[] args, boolean star) throws ParseException {
        super(args, star);
    }

    @Override
    protected void doBind() throws StreamSqlException {
        type = children[0].getType();
    }

    @Override
    protected void aggregateFillCode_Declarations(StringBuilder code) {
        code.append("\t" + getType().primitiveJavaType() + " sum;\n");

    }

    @Override
    protected void aggregateFillCode_clear(StringBuilder code) {
        code.append("\t\tsum=0;\n");
    }

    @Override
    protected void aggregateFillCode_getValue(StringBuilder code) {
        code.append("\t\treturn sum;\n");

    }

    @Override
    protected void aggregateFillCode_newData(StringBuilder code) throws StreamSqlException {
        fillCode_InputDefVars(inputDef.getColumnDefinitions(), code);
        
        code.append("\t\tsum+=col" + children[0].getColumnName());
        code.append(";\n");
    }
}
