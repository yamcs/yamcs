package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class ResourceNotFoundException extends StreamSqlException {
	public ResourceNotFoundException(String name) {
		super(ErrCode.RESOURCE_NOT_FOUND, "Stream or table '"+name+"' not found");
	}
}
