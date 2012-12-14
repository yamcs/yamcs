package org.yamcs.yarch.streamsql;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.DbReaderStream;

import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;


public class AndExpression extends Expression {
    public AndExpression(List<Expression>list) throws ParseException {
        super(list.toArray(new Expression[0]));
    }

    @Override
    public Expression addFilter(DbReaderStream tableStream) throws StreamSqlException {
        ArrayList<Expression>new_expressions=new ArrayList<Expression>();
        for(Expression expr:children) {
            Expression new_expr=expr.addFilter(tableStream);
            if(new_expr!=null) new_expressions.add(new_expr);
        }
        if(new_expressions.size()==0) return null;
        else if(new_expressions.size()==1) return new_expressions.get(0);
        children=new_expressions.toArray(new Expression[0]);
        return this;
    }



    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        boolean first=true;
        code.append("(");
        for(Expression expr:children) {
            if(!first) code.append("&&");
            else first=false;
            expr.fillCode_getValueReturn(code);
        }
        code.append(")");
    }

   

    @Override
    public void doBind() throws StreamSqlException {
        for(Expression c:children) {
            if(c.getType()!=DataType.BOOLEAN) throw new GenericStreamSqlException("'"+c+"' is not of type boolean");
        }
        type=DataType.BOOLEAN;
    }

    @Override
    public String toString() {
        StringBuffer sb=new StringBuffer();
        boolean first=true;
        for(Expression expr:children) {
            if(first) first=false;
            else sb.append(" AND ");
            sb.append("(");
            sb.append(expr.toString());
            sb.append(")");
        }
        return sb.toString();
    }
}
