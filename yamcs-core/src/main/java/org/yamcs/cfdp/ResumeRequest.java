package org.yamcs.cfdp;

/**
 * A Resume.request is a primitive that requests a certain paused transaction to be resumed*
 *
 * @author ddw
 *
 */

public class ResumeRequest extends CfdpRequest {

    private CfdpOutgoingTransfer transfer;

    public ResumeRequest(CfdpOutgoingTransfer transfer) {
        super(CfdpRequestType.RESUME);
        this.transfer = transfer;
    }

    public CfdpOutgoingTransfer getTransfer() {
        return this.transfer;
    }

}
