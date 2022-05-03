package org.yamcs.mdb;

public class CommandEncodingException extends XtceProcessingException {
    TcProcessingContext context;

    public CommandEncodingException(String msg) {
        super(msg);
    }

    public CommandEncodingException(TcProcessingContext context, String msg) {
        super(msg);
        this.context = context;
    }
}
