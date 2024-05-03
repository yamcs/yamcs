package org.yamcs.tctm.pus.services.filetransfer.thirteen;

import java.util.Objects;

import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.filetransfer.FileTransferId;
import org.yamcs.protobuf.TransferDirection;


public class S13TransactionId extends FileTransferId{
    S13UniqueId uniquenessId;
    
    public S13TransactionId(long remoteId, long transferId, long largePacketTransactionId, TransferDirection direction) {
        super(remoteId, transferId);
        this.uniquenessId = new S13UniqueId(largePacketTransactionId, direction);
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
        return transferId == other.transferId && initiatorEntityId == other.initiatorEntityId && uniquenessId == other.uniquenessId;
    }

    public long getLargePacketTransactionId() {
        return this.uniquenessId.getLargePacketTransactionId();
    }

    public TransferDirection getTransferDirection() {
        return this.uniquenessId.getTransferDirection();
    }

    public S13UniqueId getUniquenessId() {
        return this.uniquenessId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(initiatorEntityId, transferId, uniquenessId);
    }

    @Override
    public String toString() {
        return "RemoteID: " + initiatorEntityId + " | " + "LargePacketTransactionID: "  + uniquenessId.largePacketTransactionId + " | " + "TransferInstanceID: " + transferId + " | " + "Direction: " + uniquenessId.direction.name();
    }

    static public class S13UniqueId {
        protected TransferDirection direction;
        protected long largePacketTransactionId;
        
        public S13UniqueId(long largePacketTransactionId, TransferDirection direction) {
            this.direction = direction;
            this.largePacketTransactionId = largePacketTransactionId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(largePacketTransactionId, direction);
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
            S13UniqueId other = (S13UniqueId) o;
            return largePacketTransactionId == other.largePacketTransactionId && direction == other.direction;
        }

        public long getLargePacketTransactionId() {
            return this.largePacketTransactionId;
        }

        public TransferDirection getTransferDirection() {
            return this.direction;
        }
    }
}