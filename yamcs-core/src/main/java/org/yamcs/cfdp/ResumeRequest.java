package org.yamcs.cfdp;

import org.yamcs.filetransfer.FileTransfer;

/**
 * A Resume.request is a primitive that requests a certain paused transaction to be resumed*
 */
public class ResumeRequest extends CfdpRequest {

    private OngoingCfdpTransfer transfer;

    public ResumeRequest(FileTransfer transfer) {
        super(CfdpRequestType.RESUME);
        if (!(transfer instanceof OngoingCfdpTransfer)) {
            throw new IllegalArgumentException();
        }
        this.transfer = (OngoingCfdpTransfer) transfer;
    }

    public OngoingCfdpTransfer getTransfer() {
        return this.transfer;
    }
}
