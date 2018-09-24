package org.yamcs.yarch.streamsql;

@SuppressWarnings("serial")
public class NotSupportedException extends StreamSqlException {

    public NotSupportedException(String item) {
        super(ErrCode.NOT_SUPPORTED, "'" + item + "' not supported");
    }
}
