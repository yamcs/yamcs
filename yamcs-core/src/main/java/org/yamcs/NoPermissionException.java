package org.yamcs;

import org.yamcs.YamcsException;

public class NoPermissionException extends YamcsException {
    public NoPermissionException(String message) {
        super(message);
    }

}
