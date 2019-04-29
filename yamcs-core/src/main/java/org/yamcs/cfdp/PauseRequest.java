package org.yamcs.cfdp;

/**
 * A Pause.request is a primitive that requests a certain transaction to be paused*
 *
 * @author ddw
 *
 */

public class PauseRequest extends CfdpRequest {

    private CfdpTransaction transfer;

    public PauseRequest(CfdpTransaction transfer) {
        super(CfdpRequestType.PAUSE);
        this.transfer = transfer;
    }

    public CfdpTransaction getTransfer() {
        return this.transfer;
    }

}
