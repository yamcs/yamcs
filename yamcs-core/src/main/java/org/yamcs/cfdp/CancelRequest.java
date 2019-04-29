package org.yamcs.cfdp;

/**
 * A Cancel.request is a primitive that requests a certain transaction canceled*
 *
 * @author ddw
 *
 */

public class CancelRequest extends CfdpRequest {

    private CfdpTransaction transfer;

    public CancelRequest(CfdpTransaction transfer) {
        super(CfdpRequestType.CANCEL);
        this.transfer = transfer;
    }

    public CfdpTransaction getTransfer() {
        return this.transfer;
    }

}
