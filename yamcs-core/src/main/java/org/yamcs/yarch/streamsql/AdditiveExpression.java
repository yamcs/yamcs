package org.yamcs.yarch.streamsql;

import java.util.List;

import org.yamcs.yarch.streamsql.AddOp;
import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class AdditiveExpression extends Expression {
    List<AddOp> ops;

    public AdditiveExpression(List<Expression> exprs, List<AddOp> ops) throws ParseException {
        super(exprs.toArray(new Expression[0]));
        this.ops=ops;
        constant=true;
        for (Expression expr:exprs) {
            if(!expr.isConstant()) { 
                constant=false;
                break;
            }
        }
    }

    @Override
    public void doBind() throws StreamSqlException {
        type=children[0].getType();
    }

    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        code.append("(");
        children[0].fillCode_getValueReturn(code);
        for(int i=0;i<ops.size();i++) {
            code.append(ops.get(i).getSign());
            children[i+1].fillCode_getValueReturn(code);
        }
        code.append(")");
    }

}

