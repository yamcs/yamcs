package org.yamcs.yarch.streamsql;

@SuppressWarnings("serial")
public class ResourceNotFoundException extends StreamSqlException {

    public ResourceNotFoundException(String name) {
        super(ErrCode.RESOURCE_NOT_FOUND, "Stream or table '" + name + "' not found");
    }
}
