package org.yamcs.tctm.pus.services.filetransfer.thirteen.requests;

import org.yamcs.filetransfer.FileTransfer;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.OngoingS13Transfer;

/**
 * A Resume.request is a primitive that requests a certain paused transaction to
 * be resumed*
 *
 * @author ddw
 *
 */

public class ResumeRequest extends S13Request {

    private OngoingS13Transfer transfer;

    public ResumeRequest(FileTransfer transfer) {
        super(S13RequestType.RESUME);
        if (!(transfer instanceof OngoingS13Transfer)) {
            throw new IllegalArgumentException();
        }
        this.transfer = (OngoingS13Transfer) transfer;
    }

    public OngoingS13Transfer getTransfer() {
        return this.transfer;
    }

}
