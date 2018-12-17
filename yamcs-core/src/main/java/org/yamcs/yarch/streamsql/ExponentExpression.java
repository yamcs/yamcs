package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;


public class ExponentExpression extends Expression {

	public ExponentExpression(Expression retExpr, Expression expr) throws ParseException {
		super(new Expression[] {retExpr, expr});
	}

	public void setBase(Expression expr) {
		// TODO Auto-generated method stub
		
	}

	public void setExponent(Expression expr) {
		// TODO Auto-generated method stub
		
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
