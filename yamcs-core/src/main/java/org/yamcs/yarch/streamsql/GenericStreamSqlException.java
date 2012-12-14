package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;

import org.yamcs.yarch.streamsql.StreamSqlException;

public class GenericStreamSqlException extends StreamSqlException {
	public GenericStreamSqlException(String msg) {
		super(ErrCode.ERROR, msg);
	}
}