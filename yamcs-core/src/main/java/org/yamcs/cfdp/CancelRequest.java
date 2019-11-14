package org.yamcs.cfdp;

/**
 * A Cancel.request is a primitive that requests a certain transaction canceled*
 *
 * @author ddw
 *
 */

public class CancelRequest extends CfdpRequest {

    private CfdpTransfer transfer;

    public CancelRequest(CfdpTransfer transfer) {
        super(CfdpRequestType.CANCEL);
        this.transfer = transfer;
    }

    public CfdpTransfer getTransfer() {
        return this.transfer;
    }

}
