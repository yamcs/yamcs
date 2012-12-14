package org.yamcs.api;

/**
 * this was created because hornetq in its last incarnation throws Exception instead of HornetqException
 * Should be used for problems with encoding/decoding and sending/receiving messages via hornetq. 
 */
public class YamcsApiException extends Exception {
    private static final long serialVersionUID = 1L;

    public YamcsApiException(String message) {
        super(message);
    }
    
    public YamcsApiException(String message, Throwable t) {
        super(message,t);
    }
}
