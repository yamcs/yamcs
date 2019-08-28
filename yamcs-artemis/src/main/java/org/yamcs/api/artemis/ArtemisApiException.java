package org.yamcs.api.artemis;

/**
 * this was created because hornetq in its last incarnation throws Exception instead of HornetqException Should be used
 * for problems with encoding/decoding and sending/receiving messages via hornetq.
 */
public class ArtemisApiException extends Exception {

    private static final long serialVersionUID = 1L;

    public ArtemisApiException(String message) {
        super(message);
    }

    public ArtemisApiException(String message, Throwable t) {
        super(message, t);
    }
}
