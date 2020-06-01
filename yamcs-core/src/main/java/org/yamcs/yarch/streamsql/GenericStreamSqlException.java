package org.yamcs.yarch.streamsql;

@SuppressWarnings("serial")
public class GenericStreamSqlException extends StreamSqlException {

    public GenericStreamSqlException(String msg) {
        super(ErrCode.ERROR, msg);
    }
}
