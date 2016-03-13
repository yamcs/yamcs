package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.FieldReturnCompiledExpression;

import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

/**
 * Represents a column in a query, for example x and y below:
 * select x from table where y &gt; 0
 * @author nm
 *
 */
public class ColumnExpression extends Expression {
    String name;

    //after binding
    ColumnDefinition cdef;

    ColumnExpression(String name) throws ParseException {
        super(null);
        this.name=name;
        this.colName=name;
    }

    public String getName() {
        return name;
    }

    @Override
    public void doBind() throws StreamSqlException {
        cdef=inputDef.getColumn(name);
        if(cdef==null) throw new GenericStreamSqlException("'"+name+"' is not an input column");
        type=cdef.getType();
    }


    @Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        code.append("col"+name);
    }

    @Override 
    public CompiledExpression compile() throws StreamSqlException {
        return new FieldReturnCompiledExpression(name, cdef);
    }
    @Override
    public String toString() {
        return name;
    }
}