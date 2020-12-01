package org.yamcs.cfdp;

public interface TransferMonitor {
    void stateChanged(OngoingCfdpTransfer cfdpTransfer);
}
