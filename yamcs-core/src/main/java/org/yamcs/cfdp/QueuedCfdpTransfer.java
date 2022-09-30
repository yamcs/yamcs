package org.yamcs.cfdp;

import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.TimeEncoding;

public class QueuedCfdpTransfer implements CfdpFileTransfer {
    final FilePutRequest putRequest;
    final long id;
    TransferState state = TransferState.QUEUED;
    String failureReason;
    long creationTime;

    public QueuedCfdpTransfer(long id, long creationTime, FilePutRequest putRequest) {
        this.id = id;
        this.putRequest = putRequest;
        this.creationTime = creationTime;
    }

    @Override
    public String getBucketName() {
        return putRequest.getBucket().getName();
    }

    @Override
    public String getObjectName() {
        return putRequest.getSourceFileName();
    }

    @Override
    public String getRemotePath() {
        return putRequest.getDestinationFileName();
    }

    @Override
    public TransferDirection getDirection() {
        return TransferDirection.UPLOAD;
    }

    @Override
    public long getTotalSize() {
        return putRequest.getFileLength();
    }

    @Override
    public long getTransferredSize() {
        return 0;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public TransferState getTransferState() {
        return state;
    }

    @Override
    public boolean isReliable() {
        return putRequest.isAcknowledged();
    }

    @Override
    public String getFailuredReason() {
        return failureReason;
    }

    @Override
    public long getStartTime() {
        return TimeEncoding.INVALID_INSTANT;
    }

    @Override
    public boolean pausable() {
        return false;
    }

    @Override
    public boolean cancellable() {
        return true;
    }

    @Override
    public CfdpTransactionId getTransactionId() {
        return null;
    }

    public long getSourceId() {
        return putRequest.getSourceId();
    }

    @Override
    public long getDestinationId() {
        return putRequest.getDestinationCfdpEntityId();
    }

    public void setTransferState(TransferState state) {
        this.state = state;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public long getCreationTime() {
        return creationTime;
    }
}
