package org.yamcs.filetransfer;

public interface TransferMonitor {
    void stateChanged(FileTransfer cfdpTransfer);
}
