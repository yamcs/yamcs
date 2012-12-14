package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class NotSupportedException extends StreamSqlException
{
	public NotSupportedException(String item) {
		super(ErrCode.NOT_SUPPORTED, "'" + item + "' not supported");
	}
}
