package org.yamcs.cfdp;

/**
 * A Cancel.request is a primitive that requests a certain transaction canceled*
 *
 * @author ddw
 *
 */

public class CancelRequest extends CfdpRequest {

    private CfdpOutgoingTransfer transfer;

    public CancelRequest(CfdpOutgoingTransfer transfer) {
        super(CfdpRequestType.CANCEL);
        this.transfer = transfer;
    }

    public CfdpOutgoingTransfer getTransfer() {
        return this.transfer;
    }

}
