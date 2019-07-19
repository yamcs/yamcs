package org.yamcs;

@SuppressWarnings("serial")
public class ErrorInCommand extends YamcsException {

    public ErrorInCommand(String message) {
        super(message);
    }
}
