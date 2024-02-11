package org.yamcs.tctm.pus.services.filetransfer.thirteen.requests;

public abstract class S13Request {

    private S13RequestType type;

    enum S13RequestType {
        PUT,
        PAUSE,
        RESUME,
        CANCEL
    }

    protected S13Request(S13RequestType type) {
        this.type = type;
    }

    public S13RequestType getType() {
        return this.type;
    }

}
