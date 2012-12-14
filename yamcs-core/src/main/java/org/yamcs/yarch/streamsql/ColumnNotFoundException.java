package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class ColumnNotFoundException extends StreamSqlException {
	public ColumnNotFoundException(String name) {
		super(ErrCode.COLUMN_NOT_FOUND, "'"+name+"' is not part of the columns");
	}
}
