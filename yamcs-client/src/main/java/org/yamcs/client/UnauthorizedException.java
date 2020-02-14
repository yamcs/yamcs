package org.yamcs.client;

@SuppressWarnings("serial")
public class UnauthorizedException extends ClientException {

    public UnauthorizedException() {
        super("Unauthorized");
    }

    public UnauthorizedException(ExceptionData restData) {
        super(restData);
    }
}
