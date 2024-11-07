package org.yamcs.mdb;

public class CommandEncodingException extends XtceProcessingException {
    private static final long serialVersionUID = 1L;
    TcProcessingContext context;

    public CommandEncodingException(String msg) {
        super(msg);
    }

    public CommandEncodingException(TcProcessingContext context, String msg) {
        super(msg);
        this.context = context;
    }
}
