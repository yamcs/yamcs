package org.yamcs.yarch.streamsql;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.DbReaderStream;

import org.yamcs.yarch.streamsql.ColumnExpression;
import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.IncompatibilityException;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.ValueExpression;

public class InExpression extends Expression {
    static AtomicInteger at=new AtomicInteger();
    private int count=at.incrementAndGet();
    public InExpression(Expression expr, List<Expression> list) throws ParseException {
        super(getChildren(expr,list));
    }
    
    private static Expression[] getChildren(Expression expr, List<Expression> list) {
        Expression[] c=new Expression[list.size()+1];
        c[0]=expr;
        int i=1;
        for(Expression e:list) c[i++]=e;
        return c;
    }
    
    
    @Override
    public Expression addFilter(DbReaderStream tableStream) throws StreamSqlException {
        if(!(children[0] instanceof ColumnExpression)) {
            return this;
        }
        
        for(int i=1; i<children.length;i++) {
            if(!children[1].isConstant()) return this;
        }

        ColumnExpression cexpr=(ColumnExpression) children[0];
        Set<Object> values=new HashSet<Object>();
        for(int i=1; i<children.length;i++) {
            Object cvalue;
            if(children[1] instanceof ValueExpression) {
                cvalue=((ValueExpression)children[i]).value;
            } else {
                CompiledExpression compexpr=children[i].compile();
                cvalue=compexpr.getValue(null);
            }
            values.add(cvalue);
        }
        if(tableStream.addInFilter(cexpr,values))
            return null;
        else 
            return this;
    }
    
    @Override
    protected void doBind() throws StreamSqlException {
        for(int i=1;i<children.length;i++) {
            if(!DataType.compatible(children[i].getType(), children[0].getType())) {
                throw new IncompatibilityException(children[i]+" is of different type than "+children[0]+"("+children[i].getType()+" versus "+children[0].getType()+")");
            }
        }
        type=DataType.BOOLEAN;
    }
    
    @Override
    protected void fillCode_Declarations(StringBuilder code) {
        code.append("\tjava.util.Set inSet"+count+"=new java.util.HashSet();\n");
    }
    
    @Override
    protected void fillCode_Constructor(StringBuilder code) throws StreamSqlException {
        for(int i=1;i<children.length;i++) {
            if(children[i].isConstant()) {
                code.append("\t\tinSet"+count+".add(");
                children[i].fillCode_getValueReturn(code);
                code.append(");\n");
            }
        }
    }
    @Override
    protected
    void fillCode_getValueBody(StringBuilder code)  throws StreamSqlException{
        for(int i=1;i<children.length;i++) {
            if(!children[i].isConstant()) {
                code.append("\t\tinSet"+count+".add(");
                children[i].fillCode_getValueReturn(code);
                code.append(");\n");
            }
        }
    }
    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        code.append("inSet"+count+".contains(");
        children[0].fillCode_getValueReturn(code);
        code.append(")");
    }


    
    @Override
    public String toString() {
        StringBuilder sb=new StringBuilder();
        sb.append(children[0]).append(" IN (");
        boolean first=true;
        for(int i=1;i<children.length;i++) {
            if(!first)sb.append(", ");
            else first=false;
            sb.append(children[i].toString());
        }
        sb.append(")");
        return sb.toString();
    }

}
