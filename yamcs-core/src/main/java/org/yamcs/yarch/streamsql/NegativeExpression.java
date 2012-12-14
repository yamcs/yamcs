package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.streamsql.Expression;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;


public class NegativeExpression extends Expression {

	public NegativeExpression(Expression expr) throws ParseException {
		super(new Expression[] {expr});
		constant=expr.isConstant();
	}

  @Override
  public void doBind() throws StreamSqlException {
      type=children[0].getType();
  }

  @Override
  public void fillCode_getValueReturn(StringBuilder code) throws StreamSqlException {
    // TODO Auto-generated method stub
    
  }
	
}
