package org.yamcs.yarch.streamsql;

@SuppressWarnings("serial")
public class ColumnNotFoundException extends StreamSqlException {

    public ColumnNotFoundException(String name) {
        super(ErrCode.COLUMN_NOT_FOUND, "'" + name + "' is not part of the columns");
    }
}
