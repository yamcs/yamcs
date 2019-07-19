package org.yamcs;

@SuppressWarnings("serial")
public class NoPermissionException extends YamcsException {

    public NoPermissionException(String message) {
        super(message);
    }
}
