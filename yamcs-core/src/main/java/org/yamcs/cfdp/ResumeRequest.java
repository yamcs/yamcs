package org.yamcs.cfdp;

/**
 * A Resume.request is a primitive that requests a certain paused transaction to be resumed*
 *
 * @author ddw
 *
 */

public class ResumeRequest extends CfdpRequest {

    private OngoingCfdpTransfer transfer;

    public ResumeRequest(CfdpTransfer transfer) {
        super(CfdpRequestType.RESUME);
        if(!(transfer instanceof OngoingCfdpTransfer)) {
            throw new IllegalArgumentException();
        }
        this.transfer = (OngoingCfdpTransfer)transfer;
    }

    public OngoingCfdpTransfer getTransfer() {
        return this.transfer;
    }

}
