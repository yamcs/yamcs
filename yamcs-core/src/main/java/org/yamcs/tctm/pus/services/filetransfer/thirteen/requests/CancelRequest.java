package org.yamcs.tctm.pus.services.filetransfer.thirteen.requests;

import org.yamcs.filetransfer.FileTransfer;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.OngoingS13Transfer;

/**
 * A Cancel.request is a primitive that requests a certain transaction canceled*
 *
 * @author ddw
 *
 */

public class CancelRequest extends S13Request {

    private OngoingS13Transfer transfer;

    public CancelRequest(FileTransfer transfer) {
        super(S13RequestType.CANCEL);
        if (!(transfer instanceof OngoingS13Transfer)) {
            throw new IllegalArgumentException();
        }
        this.transfer = (OngoingS13Transfer) transfer;
    }

    public OngoingS13Transfer getTransfer() {
        return this.transfer;
    }

}
