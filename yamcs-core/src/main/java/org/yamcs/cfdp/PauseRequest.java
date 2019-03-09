package org.yamcs.cfdp;

/**
 * A Pause.request is a primitive that requests a certain transaction to be paused*
 *
 * @author ddw
 *
 */

public class PauseRequest extends CfdpRequest {

    private CfdpTransfer transfer;

    public PauseRequest(CfdpTransfer transfer) {
        super(CfdpRequestType.PAUSE);
        this.transfer = transfer;
    }

    public CfdpTransfer getTransfer() {
        return this.transfer;
    }

}
