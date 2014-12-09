package org.yamcs.yarch.rocksdb;

import java.util.Set;

import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.DbReaderStream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.streamsql.ColumnExpression;
import org.yamcs.yarch.streamsql.RelOp;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class RdbTableReaderStream extends AbstractStream implements Runnable, DbReaderStream {

	protected RdbTableReaderStream(YarchDatabase ydb, String name, TupleDefinition definition) {
		super(ydb, name, definition);
	}

	@Override
	public boolean addRelOpFilter(ColumnExpression cexpr, RelOp relOp, Object value) throws StreamSqlException {
		return false;
	}

	@Override
	public boolean addInFilter(ColumnExpression cexpr, Set<Object> values) throws StreamSqlException {
		return false;
	}

	@Override
	public void run() {
		
	}

	@Override
	public void start() {
		
	}

	@Override
	protected void doClose() {
	}

}
