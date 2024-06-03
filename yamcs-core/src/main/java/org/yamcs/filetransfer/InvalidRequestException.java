package org.yamcs.filetransfer;

@SuppressWarnings("serial")
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
