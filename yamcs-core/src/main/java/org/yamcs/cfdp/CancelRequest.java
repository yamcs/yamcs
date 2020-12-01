package org.yamcs.cfdp;

/**
 * A Cancel.request is a primitive that requests a certain transaction canceled*
 *
 * @author ddw
 *
 */

public class CancelRequest extends CfdpRequest {

    private OngoingCfdpTransfer transfer;

    public CancelRequest(CfdpTransfer transfer) {
        super(CfdpRequestType.CANCEL);
        if(!(transfer instanceof OngoingCfdpTransfer)) {
            throw new IllegalArgumentException();
        }
        this.transfer = (OngoingCfdpTransfer)transfer;
    }

    public OngoingCfdpTransfer getTransfer() {
        return this.transfer;
    }

}
