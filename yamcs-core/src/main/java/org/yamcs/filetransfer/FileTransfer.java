package org.yamcs.filetransfer;

import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;

public interface FileTransfer {
    /**
     * return the name of the bucket where the file is being transfered to/from.
     * <p>
     * Could be null for incoming transfers. For the CFDP service, the bucket is determined by the source or destination
     * entity id and it is not null. However if the bucket was determined by the filename which is known only when the
     * metadata packet is received, this could be null.
     */
    String getBucketName();

    /**
     * return the name of the object (file) which is being transfered. This is the filename on the local (Yamcs) site.
     * <p>
     * Can be null for incoming transfers - for example CFDP can start a transfer without knowing the filename if the
     * first metadata packet has been lost.
     */
    String getObjectName();

    /**
     * return the remote path of the file which is being transfered.
     * <p>
     * Can be null for incoming transfers - for example CFDP can start a transfer without having this information if the
     * first metadata packet has been lost.
     */
    String getRemotePath();

    Long getLocalEntityId();

    Long getRemoteEntityId();

    TransferDirection getDirection();

    /**
     * return the file size in bytes or -1 if the size is not known.
     * <p>
     * For the CFDP service the incoming files can be unbounded (but this is not yet supported) or the size will be part
     * of the metadata packet which may be missing.
     */
    long getTotalSize();

    long getTransferredSize();

    long getId();

    TransferState getTransferState();

    boolean isReliable();

    String getFailuredReason();

    long getCreationTime();

    long getStartTime();

    String getTransferType();

    boolean pausable();

    boolean cancellable();
}
