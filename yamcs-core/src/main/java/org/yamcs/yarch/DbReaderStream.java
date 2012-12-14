package org.yamcs.yarch;

import java.util.Set;

import org.yamcs.yarch.streamsql.ColumnExpression;
import org.yamcs.yarch.streamsql.RelOp;
import org.yamcs.yarch.streamsql.StreamSqlException;


public interface DbReaderStream {
	public boolean addRelOpFilter(ColumnExpression cexpr, RelOp relOp, Object value) throws StreamSqlException;
	public boolean addInFilter(ColumnExpression cexpr, Set<Object> values) throws StreamSqlException;

}
