package org.yamcs.cfdp;

import org.yamcs.protobuf.TransferDirection;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Bucket;

public class QueuedCfdpOutgoingTransfer implements CfdpFileTransfer {

    private final PutRequest putRequest;
    private final long initiatorEntityId;
    private final long id;
    private TransferState state = TransferState.QUEUED;
    private String failureReason;
    private final long creationTime;
    private final Bucket bucket;
    private final Integer customPduSize;
    private final Integer customPduDelay;
    private final String transferType;

    public QueuedCfdpOutgoingTransfer(long initiatorEntityId, long id, long creationTime, PutRequest putRequest,
            Bucket bucket, Integer customPduSize, Integer customPduDelay) {
        this.initiatorEntityId = initiatorEntityId;
        this.id = id;
        this.putRequest = putRequest;
        this.creationTime = creationTime;
        this.bucket = bucket;
        this.customPduSize = customPduSize;
        this.customPduDelay = customPduDelay;
        this.transferType = OngoingCfdpTransfer.getTransferType(putRequest.getMetadata());
    }

    @Override
    public String getBucketName() {
        return bucket != null ? bucket.getName() : null;
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

    @Override
    public long getDestinationId() {
        return putRequest.getDestinationCfdpEntityId();
    }

    @Override
    public String getTransferType() {
        return transferType;
    }

    public void setTransferState(TransferState state) {
        this.state = state;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public long getInitiatorEntityId() {
        return initiatorEntityId;
    }

    public PutRequest getPutRequest() {
        return putRequest;
    }

    public Bucket getBucket() {
        return bucket;
    }

    public Integer getCustomPduSize() {
        return customPduSize;
    }

    public Integer getCustomPduDelay() {
        return customPduDelay;
    }
}
