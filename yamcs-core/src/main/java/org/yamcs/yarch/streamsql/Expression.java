package org.yamcs.yarch.streamsql;


import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.janino.SimpleCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.DbReaderStream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

public abstract class Expression {
    protected DataType type=null;
    protected Expression[] children;
    protected TupleDefinition inputDef;

    protected boolean hasAggregates;
    protected boolean constant=false;
    String colName;
    static Logger log = LoggerFactory.getLogger(Expression.class);

    public Expression(Expression[] children) {
        this.children=children;
        hasAggregates=false;
        if(children!=null) {
            for(Expression c:children) {
                if(c.isAggregate() || c.hasAggregates) {
                    hasAggregates=true;
                }
            }
        }
        colName=String.format("%s0x%xd", this.getClass().getSimpleName(), this.hashCode());
    }
    protected boolean isAggregate() {return false;}


    final public boolean isConstant() {
        return constant;
    }


    /**
     * add a filter to the table if applicable and returns an expression where the condition is removed.
     * @param tableStream
     * @return
     * @throws StreamSqlException 
     */
    public Expression addFilter(DbReaderStream tableStream) throws StreamSqlException {
        // by default do nothing
        return this;
    }

    public void collectAggregates(List<AggregateExpression> list) {
        if(isAggregate()) 	list.add((AggregateExpression)this);
        else if(children!=null) {
            for(Expression c:children) {
                if(c.hasAggregates || c.isAggregate()) {
                    c.collectAggregates(list);
                }
            }
        }
    }


    protected abstract void doBind() throws StreamSqlException;
    public void bind(TupleDefinition inputDef2) throws StreamSqlException {
        this.inputDef=inputDef2;
        if(children!=null) {
            for(Expression c:children) {
                c.bind(inputDef);
            }
        }
        doBind();
    }


    public DataType getType() {
        return type;
    }

    protected void fillCode_Declarations(StringBuilder code) {
        //do nothing by default
    }
    protected void fillCode_Constructor(StringBuilder code) throws StreamSqlException {
        //do nothing by default
    }
    protected void fillCode_AllInputDefVars(StringBuilder code){
        for(ColumnDefinition cd:inputDef.getColumnDefinitions()) {
            if (cd.getType().val != DataType._type.PROTOBUF) {
                code.append("\t\t"+cd.getType().javaType()+" col"+cd.getName()+"=null;\n")
                .append("\t\tif(tuple.hasColumn(\""+cd.getName()+"\")) {\n")
                .append("\t\t\tcol"+cd.getName()+"=("+cd.getType().javaType()+")tuple.getColumn(\""+cd.getName()+"\");\n")
                .append("\t\t}\n");
            }
        }
    }
    protected void fillCode_getValueBody(StringBuilder code)  throws StreamSqlException{};

    protected abstract void fillCode_getValueReturn(StringBuilder code)  throws StreamSqlException;



    private static AtomicInteger counter=new AtomicInteger();
    public CompiledExpression compile() throws StreamSqlException {
        String className = "Expression"+counter.incrementAndGet();
        StringBuilder source=new StringBuilder();
        source.append("package org.yamcs.yarch;\n")
        .append("public class "+className+" implements CompiledExpression {\n")
        .append("\tColumnDefinition cdef;\n");
        fillCode_Declarations(source);

        source.append("\tpublic "+className+"(ColumnDefinition cdef) {\n")
        .append("\t\tthis.cdef=cdef;\n");
        fillCode_Constructor(source);
        source.append("\t}\n");

        source.append("\tpublic Object getValue(Tuple tuple) {\n"); 
        if(!isConstant()) fillCode_AllInputDefVars(source);
        fillCode_getValueBody(source);

        // source.append("Value colid=t.getColumn(\"id\");\n");
        source.append("\n\t\treturn ");
        fillCode_getValueReturn(source);
        source.append(";\n");
        source.append("\t}\n")
        .append("\tpublic ColumnDefinition getDefinition() {\n")
        .append("\t\treturn cdef;\n")
        .append("\t}\n")
        .append("}\n");
        
        try {
            SimpleCompiler compiler=new SimpleCompiler();
            compiler.cook(new StringReader(source.toString()));
            @SuppressWarnings("unchecked")
            Class<CompiledExpression> cexprClass = (Class<CompiledExpression>) compiler.getClassLoader().loadClass("org.yamcs.yarch."+className);
            Constructor<CompiledExpression> cexprConstructor=cexprClass.getConstructor(ColumnDefinition.class);
            ColumnDefinition cdef=new ColumnDefinition(colName, type);
            return cexprConstructor.newInstance(cdef);
        } catch (Exception e) {
            log.warn("Got exception when compiling {} ", source.toString(), e);
            throw new StreamSqlException(ErrCode.COMPILE_ERROR, e.toString()); 
        }
    }


    /**
     * when the expression behaves like a column expression, this is the column name 
     * @return
     */
    public String getColName() {
        return colName;
    }
}
