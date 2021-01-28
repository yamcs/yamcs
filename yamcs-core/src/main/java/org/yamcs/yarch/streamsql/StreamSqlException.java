package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.YarchException;

/**
 * Exception thrown for errors in sql queries.
 * <p>
 * For errors related to the storage engine, the {@link YarchException} shall be used. 
 * 
 * @author nm
 *
 */
public class StreamSqlException extends Exception {

    public enum ErrCode {
        ERROR,
        RESOURCE_EXISTS,
        NOT_A_STREAM,
        NONE_SPECIFIED,
        INCOMPATIBLE,
        NOT_IMPLEMENTED,
        COLUMN_NOT_FOUND,
        AGGREGATE_IN_AGGREGATE,
        COMPILE_ERROR,
        NOT_SUPPORTED,
        RESOURCE_NOT_FOUND,
        AGGREGATE_WITHOUT_WINDOW,
        INVALID_HISTOGRAM_COLUMN,
        INVALID_INDEX_COLUMN,
        WRONG_ARG_COUNT,
        BAD_ARG_TYPE, DUPLICATE_KEY;
    };

    ErrCode errCode;

    public StreamSqlException(ErrCode code, String msg) {
        super(code + " " + msg);
        this.errCode = code;
    }

    public StreamSqlException(ErrCode code) {
        this.errCode = code;
    }

    @Override
    public String toString() {
        return errCode + ":  " + getMessage();
    }
}

@SuppressWarnings("serial")
class ResourceAlreadyExistsException extends StreamSqlException {
    public ResourceAlreadyExistsException(String name) {
        super(ErrCode.RESOURCE_EXISTS, "There is already a table or stream with the name '" + name + "'");
    }
}

@SuppressWarnings("serial")
class NotAStreamException extends StreamSqlException {
    public NotAStreamException(String name) {
        super(ErrCode.NOT_A_STREAM, "'" + name + "' is not an input or output stream");
    }
}

@SuppressWarnings("serial")
class NoneSpecifiedException extends StreamSqlException {
    public NoneSpecifiedException() {
        super(ErrCode.NONE_SPECIFIED, "None of the objectname or stream expression specified");
    }
}

@SuppressWarnings("serial")
class IncompatibilityException extends StreamSqlException {
    public IncompatibilityException(String reason) {
        super(ErrCode.INCOMPATIBLE, "Incompatibility detected because: " + reason);
    }
}

@SuppressWarnings("serial")
class NotImplementedException extends StreamSqlException {
    public NotImplementedException(String item) {
        super(ErrCode.NOT_IMPLEMENTED, item);
    }
}

