package org.yamcs.filetransfer;

import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;

public interface FileTransfer {
    String getBucketName();

    /**
     * return the name of the object (file) which is being transfered. This is the filename on the local (Yamcs) site.
     * <p>
     * Can be null for incoming transfers - for example CFDP can start a transfer without knowing the filename if the
     * first metadata packet has been lost.
     * 
     */
    String getObjectName();

    /**
     * return the remote path of the file which is being transfered.
     * <p>
     * Can be null for incoming transfers - for example CFDP can start a transfer without having this information if the
     * first metadata packet has been lost.
     */
    String getRemotePath();

    TransferDirection getDirection();

    long getTotalSize();

    long getTransferredSize();

    long getId();

    TransferState getTransferState();

    boolean isReliable();

    String getFailuredReason();

    long getCreationTime();

    long getStartTime();

    boolean pausable();

    boolean cancellable();
}
