package org.yamcs.yarch.streamsql;

import java.util.List;

import org.yamcs.yarch.streamsql.AggregateListExpression;
import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.FirstValExpression;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.SumExpression;

public class FunctionExpressionFactory {

	static Expression get(String name, List<Expression> args, boolean star) throws ParseException {
	    Expression[] argsa=(args==null)?null:args.toArray(new Expression[0]);
		if("SUM".equalsIgnoreCase(name)) {
			return new SumExpression(argsa,star);
		} else if("aggregatelist".equalsIgnoreCase(name)) {
            return new AggregateListExpression(argsa,star);
        } if("firstval".equalsIgnoreCase(name)) {
            return new FirstValExpression(argsa,star);
        }
		
		throw new ParseException("unknown function '"+name+"'");
	}
}
