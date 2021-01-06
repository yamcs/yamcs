package org.yamcs.cfdp;

import org.yamcs.filetransfer.FileTransfer;

public interface CfdpFileTransfer extends FileTransfer {
    CfdpTransactionId getTransactionId();
}
