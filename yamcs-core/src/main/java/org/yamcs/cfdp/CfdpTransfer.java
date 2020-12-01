package org.yamcs.cfdp;

import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;

public interface CfdpTransfer {
    String getBucketName();
    String getObjectName();
    String getRemotePath();
    TransferDirection getDirection();
    long getTotalSize();
    long getTransferredSize();
    CfdpTransactionId getTransactionId();
    long getId();
    TransferState getTransferState();
    boolean isReliable();
    String getFailuredReason();
    long getStartTime();
    boolean pausable();
    boolean cancellable();
}
