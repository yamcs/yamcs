package org.yamcs.filetransfer;

import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;

public interface FileTransfer {
    String getBucketName();
    String getObjectName();
    String getRemotePath();
    TransferDirection getDirection();
    long getTotalSize();
    long getTransferredSize();

    long getId();
    TransferState getTransferState();
    boolean isReliable();
    String getFailuredReason();
    long getStartTime();
    boolean pausable();
    boolean cancellable();
}
