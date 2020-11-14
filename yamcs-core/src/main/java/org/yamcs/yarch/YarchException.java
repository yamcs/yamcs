package org.yamcs.yarch;


public class YarchException extends RuntimeException {
    public YarchException(String string) {
        super(string);
    }
    public YarchException(Throwable t) {
        super(t);
    }
    public YarchException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
