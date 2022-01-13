package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.CompiledAggregateExpression;
import org.yamcs.yarch.CompiledFirstVal;
import org.yamcs.yarch.DataType;
import org.yamcs.utils.parser.ParseException;

public class FirstValExpression extends AggregateExpression {
    public FirstValExpression(Expression[] args, boolean star) throws ParseException {
        super(args, star);
    }

    @Override
    protected void doBind() throws StreamSqlException {
        if(star) {
            type=DataType.tuple(inputDef);
        } else if (children.length>1) {
            //TODO
        } else if (children.length==1) {
            type=inputDef.getColumn(children[0].getColumnName()).getType();
        } else {
            throw new IllegalStateException();
        }
    }

    @Override
    public CompiledAggregateExpression getCompiledAggregate() throws StreamSqlException {
        if(star)  return new CompiledFirstVal(null, star);
        String[] args=new String[children.length];
        for(int i=0; i<children.length; i++) {
            args[i]=children[i].getColumnName();
        }
        return new CompiledFirstVal(args,star);
    }
}
