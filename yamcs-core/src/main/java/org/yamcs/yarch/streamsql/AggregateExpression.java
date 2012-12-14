package org.yamcs.yarch.streamsql;


import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.CompiledAggregateExpression;
import org.yamcs.yarch.CompiledExpression;
import org.yamcs.yarch.FieldReturnCompiledExpression;
import org.yamcs.yarch.TupleDefinition;

import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

/**
 * Expressions containing aggregates are computed in two phases:
 * phase 1: the input tuples are passed to a list of expressions composed by the group by columns and the list of aggregates
 * phase 2: the output tuples of phase 1 are passed to the list of original select list. In this phase, aggregates act as a ColumnExpression
 * @author nm
 *
 */
public abstract class AggregateExpression extends Expression {
	boolean star;
	ColumnDefinition cdef;
	public AggregateExpression(Expression[] args, boolean star) throws ParseException {
		super(args);
		this.star=star;
		if(!star) {
			for(Expression c:children)
				if(c.hasAggregates) throw new ParseException("Aggregate not allowed as argument to another aggregate" );
		}
	}

	@Override 
	protected boolean isAggregate() {return true;}
	
	/**
	 * When this is called, all the children are already bound.
	 * 
	 */
	public void bindAggregate(TupleDefinition def) throws StreamSqlException {
        this.inputDef=def;
        doBind();
    }
    
	/*
	 * this is called recursively from the select top expression. We behave like if we were a column expression.
	 * the bindAggregate method takes care of the type and binding children and it is called before this
	 */
	@Override
	public void bind(TupleDefinition def) throws StreamSqlException{
	    cdef=def.getColumn(colName);
        if(cdef==null) throw new GenericStreamSqlException("'"+colName+"' is not an input column");
        type=cdef.getType();
	}
	
	@Override
    public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
        code.append("col"+colName);
    }

	@Override 
    public CompiledExpression compile() throws StreamSqlException {
        return new FieldReturnCompiledExpression(colName, cdef);
    }


	abstract public CompiledAggregateExpression getCompiledAggregate() throws StreamSqlException;
	
}