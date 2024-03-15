package org.yamcs.tctm.pus.services.filetransfer.thirteen;

import java.util.Objects;

import org.yamcs.filetransfer.FileTransferId;
import org.yamcs.protobuf.TransferDirection;


public class S13TransactionId extends FileTransferId{
    protected TransferDirection direction;
    
    public S13TransactionId(long remoteId, long transferId, TransferDirection direction) {
        super(remoteId, transferId);
        this.direction = direction;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (getClass() != o.getClass()) {
            return false;
        }
        S13TransactionId other = (S13TransactionId) o;
        return transferId == other.transferId && initiatorEntityId == other.initiatorEntityId && direction == other.direction;
    }

    public long getLargePacketTransactionId() {
        return transferId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(initiatorEntityId, transferId);
    }
    
    public TransferDirection getTransferDirection() {
        return direction;
    }

    @Override
    public String toString() {
        return "Remote ID: " + initiatorEntityId + " | " + "LargePacketTransactionId: "  + transferId;
    }
}
