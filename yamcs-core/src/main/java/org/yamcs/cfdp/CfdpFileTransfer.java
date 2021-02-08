package org.yamcs.cfdp;

import org.yamcs.filetransfer.FileTransfer;

public interface CfdpFileTransfer extends FileTransfer {
    /**
     * Get the CFDP transaction id. Returns null for queued transfers.
     * 
     * @return
     */
    CfdpTransactionId getTransactionId();

    long getSourceId();

    long getDestinationId();
}
