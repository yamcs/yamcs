package org.yamcs.yarch;

import org.yamcs.protobuf.Cfdp.TransferState;

public class CfdpTransferState {
    private TransferState state;
    private long transferred;

    public TransferState getState() {
        return this.state;
    }

    public long getTransferredSize() {
        return this.transferred;
    }
}
