package org.yamcs.cfdp;

/**
 * A Pause.request is a primitive that requests a certain transaction to be paused*
 *
 * @author ddw
 *
 */

public class PauseRequest extends CfdpRequest {

    private CfdpOutgoingTransfer transfer;

    public PauseRequest(CfdpOutgoingTransfer transfer) {
        super(CfdpRequestType.PAUSE);
        this.transfer = transfer;
    }

    public CfdpOutgoingTransfer getTransfer() {
        return this.transfer;
    }

}
