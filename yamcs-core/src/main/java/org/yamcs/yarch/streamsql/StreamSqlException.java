package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.streamsql.StreamSqlException;

public class StreamSqlException extends Exception {
    public enum ErrCode {
        ERROR, RESOURCE_EXISTS, NOT_A_STREAM, NONE_SPECIFIED, INCOMPATIBLE, NOT_IMPLEMENTED, 
        COLUMN_NOT_FOUND, AGGREGATE_IN_AGGREGATE, COMPILE_ERROR, NOT_SUPPORTED, RESOURCE_NOT_FOUND, 
        AGGREGATE_WITHOUT_WINDOW, INVALID_HISTOGRAM_COLUMN, WRONG_ARG_COUNT, BAD_ARG_TYPE;
    };
    ErrCode errCode;
    
    public StreamSqlException(ErrCode code, String msg) {
        super(code + " " + msg);
        this.errCode = code;
    }
    public StreamSqlException(ErrCode code) {
        this.errCode = code;
    }
    
    public String toString() {
        return errCode+":  "+getMessage();
    }
}

class StreamAlreadyExistsException extends StreamSqlException {
    public StreamAlreadyExistsException(String name) {
        super(ErrCode.RESOURCE_EXISTS, "There is already a table or stream with the name '" + name + "'");
    }
}

class NotAStreamException extends StreamSqlException {
    public NotAStreamException(String name) {
        super(ErrCode.NOT_A_STREAM, "'" + name + "' is not an input or output stream");
    }
}

class NoneSpecifiedException extends StreamSqlException {
    public NoneSpecifiedException() {
        super(ErrCode.NONE_SPECIFIED, "None of the objectname or stream expression specified");
    }
}

class IncompatibilityException extends StreamSqlException {
    public IncompatibilityException(String reason) {
        super(ErrCode.INCOMPATIBLE, "Incompatibility detected because: " + reason);
    }
}

class NotImplementedException extends StreamSqlException {
    public NotImplementedException(String item) {
        super(ErrCode.NOT_IMPLEMENTED, item);
    }
}
