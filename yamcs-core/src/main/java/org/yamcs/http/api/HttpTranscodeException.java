package org.yamcs.http.api;

@SuppressWarnings("serial")
public class HttpTranscodeException extends Exception {

    public HttpTranscodeException(String message) {
        super(message);
    }

    public HttpTranscodeException(String message, Throwable t) {
        super(message, t);
    }
}
