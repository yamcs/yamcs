package org.yamcs.yarch.streamsql.funct;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.streamsql.CompilableAggregateExpression;
import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class CountExpression extends CompilableAggregateExpression {

    public CountExpression(Expression[] args, boolean star) throws ParseException {
        super(args, star);
    }

    @Override
    protected void doBind() throws StreamSqlException {
        type = DataType.LONG;
    }

    @Override
    protected void aggregateFillCode_Declarations(StringBuilder code) {
        code.append("\tlong count;\n");

    }

    @Override
    protected void aggregateFillCode_clear(StringBuilder code) {
        code.append("\t\tcount=0;\n");
    }

    @Override
    protected void aggregateFillCode_getValue(StringBuilder code) {
        code.append("\t\treturn count;\n");

    }

    @Override
    protected void aggregateFillCode_newData(StringBuilder code) throws StreamSqlException {
        code.append("\t\tcount++");
        code.append(";\n");
    }
}
