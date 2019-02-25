package org.yamcs.yarch;

import org.yamcs.protobuf.Cfdp.TransferDirection;

// TODO, make this a protobuf?
public class CfdpTransfer {

    private long id;
    private CfdpTransferState state;
    private Bucket bucket;
    private String object;
    private String remotePath;
    private TransferDirection transferDirection;

    private long totalSize;

    public long getId() {
        return this.id;
    }

    public CfdpTransferState getState() {
        return this.state;
    }

    public Bucket getBucket() {
        return this.bucket;
    }

    public String getObjectName() {
        return this.object;
    }

    public String getRemotePath() {
        return this.remotePath;
    }

    public TransferDirection getDirection() {
        return this.transferDirection;
    }

    public long getTotalSize() {
        return this.totalSize;
    }

    public CfdpTransfer cancel() {
        // IF cancelled, return myself, otherwise return null id, otherwise return null
        return this;
    }
}
