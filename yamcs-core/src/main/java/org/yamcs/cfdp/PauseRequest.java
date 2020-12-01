package org.yamcs.cfdp;

/**
 * A Pause.request is a primitive that requests a certain transaction to be paused*
 *
 * @author ddw
 *
 */

public class PauseRequest extends CfdpRequest {

    private OngoingCfdpTransfer transfer;

    public PauseRequest(CfdpTransfer transfer) {
        super(CfdpRequestType.PAUSE);
        if(!(transfer instanceof OngoingCfdpTransfer)) {
            throw new IllegalArgumentException();
        }
        this.transfer = (OngoingCfdpTransfer)transfer;
    }

    public OngoingCfdpTransfer getTransfer() {
        return this.transfer;
    }

}
