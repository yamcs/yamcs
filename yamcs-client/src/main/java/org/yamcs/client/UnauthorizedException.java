package org.yamcs.client;

@SuppressWarnings("serial")
public class UnauthorizedException extends ClientException {

    public UnauthorizedException() {
        super("Unauthorized");
    }

    public UnauthorizedException(String message) {
        super("Unauthorized: " + message);
    }

    public UnauthorizedException(ExceptionData restData) {
        super(restData);
    }
}
