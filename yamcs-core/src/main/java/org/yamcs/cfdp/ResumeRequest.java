package org.yamcs.cfdp;

/**
 * A Resume.request is a primitive that requests a certain paused transaction to be resumed*
 *
 * @author ddw
 *
 */

public class ResumeRequest extends CfdpRequest {

    private CfdpTransfer transfer;

    public ResumeRequest(CfdpTransfer transfer) {
        super(CfdpRequestType.RESUME);
        this.transfer = transfer;
    }

    public CfdpTransfer getTransfer() {
        return this.transfer;
    }

}
