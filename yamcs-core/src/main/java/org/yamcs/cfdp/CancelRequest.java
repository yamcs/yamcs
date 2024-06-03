package org.yamcs.cfdp;

import org.yamcs.filetransfer.FileTransfer;

/**
 * A Cancel.request is a primitive that requests a certain transaction canceled*
 */
public class CancelRequest extends CfdpRequest {

    private OngoingCfdpTransfer transfer;

    public CancelRequest(FileTransfer transfer) {
        super(CfdpRequestType.CANCEL);
        if (!(transfer instanceof OngoingCfdpTransfer)) {
            throw new IllegalArgumentException();
        }
        this.transfer = (OngoingCfdpTransfer) transfer;
    }

    public OngoingCfdpTransfer getTransfer() {
        return this.transfer;
    }
}
