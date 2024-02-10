package org.yamcs.tctm.pus.services.filetransfer.thirteen;

import org.yamcs.filetransfer.FileTransfer;

public interface S13FileTransfer extends FileTransfer {
    /**
     * Get the S13 transaction id. Returns null for queued transfers.
     * 
     * @return
     */
    S13TransactionId getTransactionId();

    long getInitiatorEntityId();

    long getDestinationId();

    String getOrigin();
}
