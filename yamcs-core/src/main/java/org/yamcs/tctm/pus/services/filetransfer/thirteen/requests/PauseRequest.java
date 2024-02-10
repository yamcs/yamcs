package org.yamcs.tctm.pus.services.filetransfer.thirteen.requests;

import org.yamcs.filetransfer.FileTransfer;
import org.yamcs.tctm.pus.services.filetransfer.thirteen.OngoingS13Transfer;

/**
 * A Pause.request is a primitive that requests a certain transaction to be
 * paused*
 *
 * @author ddw
 *
 */

public class PauseRequest extends S13Request {

    private OngoingS13Transfer transfer;

    public PauseRequest(FileTransfer transfer) {
        super(S13RequestType.PAUSE);
        if (!(transfer instanceof OngoingS13Transfer)) {
            throw new IllegalArgumentException();
        }
        this.transfer = (OngoingS13Transfer) transfer;
    }

    public OngoingS13Transfer getTransfer() {
        return this.transfer;
    }

}
