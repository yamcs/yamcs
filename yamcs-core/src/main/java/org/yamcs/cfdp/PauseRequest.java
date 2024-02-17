package org.yamcs.cfdp;

import org.yamcs.filetransfer.FileTransfer;

/**
 * A Pause.request is a primitive that requests a certain transaction to be paused*
 */
public class PauseRequest extends CfdpRequest {

    private OngoingCfdpTransfer transfer;

    public PauseRequest(FileTransfer transfer) {
        super(CfdpRequestType.PAUSE);
        if (!(transfer instanceof OngoingCfdpTransfer)) {
            throw new IllegalArgumentException();
        }
        this.transfer = (OngoingCfdpTransfer) transfer;
    }

    public OngoingCfdpTransfer getTransfer() {
        return this.transfer;
    }
}
