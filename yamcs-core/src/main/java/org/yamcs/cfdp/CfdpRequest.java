package org.yamcs.cfdp;

public abstract class CfdpRequest {

    private CfdpRequestType type;

    enum CfdpRequestType {
        PUT
    }

    protected CfdpRequest(CfdpRequestType type) {
        this.type = type;
    }

    public CfdpRequestType getType() {
        return this.type;
    }

}
