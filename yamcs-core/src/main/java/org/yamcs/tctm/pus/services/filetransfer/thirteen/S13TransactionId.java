package org.yamcs.tctm.pus.services.filetransfer.thirteen;

import java.util.Objects;

import org.yamcs.filetransfer.FileTransferId;


public class S13TransactionId extends FileTransferId{
    protected long largePacketTransactionId;

    public S13TransactionId(long initiatorEntityId, long transferId, long largePacketTransactionId) {
        super(initiatorEntityId, transferId);
        this.largePacketTransactionId = largePacketTransactionId;
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
        return transferId == other.transferId && initiatorEntityId == other.initiatorEntityId && largePacketTransactionId == other.largePacketTransactionId;
    }

    public long getLargePacketTransactionId() {
        return largePacketTransactionId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(initiatorEntityId, transferId, largePacketTransactionId);
    }
    
    @Override
    public String toString() {
        return initiatorEntityId + "_" + transferId + "_" + largePacketTransactionId;
    }
}
