package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.TupleDefinition;

import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;


public class NotExpression extends Expression {

	public NotExpression(Expression expr) throws ParseException {
		super(new Expression[]{expr});
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public void doBind() throws StreamSqlException {
	    // TODO Auto-generated method stub
	    
	  }

  @Override
  public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
    // TODO Auto-generated method stub
    
  }

}
