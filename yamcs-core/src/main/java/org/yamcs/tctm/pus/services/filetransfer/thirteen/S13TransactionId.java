package org.yamcs.tctm.pus.services.filetransfer.thirteen;

import java.util.Objects;

import org.yamcs.filetransfer.FileTransferId;
import org.yamcs.protobuf.TransferDirection;


public class S13TransactionId extends FileTransferId{
    protected long largePacketTransactionId;
    protected TransferDirection direction;
    
    public S13TransactionId(long remoteId, long transferId, long largePacketTransactionId, TransferDirection direction) {
        super(remoteId, transferId);
        this.largePacketTransactionId = largePacketTransactionId;
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
        return transferId == other.transferId && initiatorEntityId == other.initiatorEntityId && largePacketTransactionId == other.largePacketTransactionId && direction == other.direction;
    }

    public long getLargePacketTransactionId() {
        return largePacketTransactionId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(initiatorEntityId, transferId, largePacketTransactionId);
    }
    
    public TransferDirection getTransferDirection() {
        return direction;
    }

    @Override
    public String toString() {
        return "Remote ID: " + initiatorEntityId + "_" + "TransferId: "  + transferId + "_" + "LPTId: " + largePacketTransactionId;
    }
}
