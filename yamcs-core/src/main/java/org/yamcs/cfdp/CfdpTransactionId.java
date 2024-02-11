package org.yamcs.cfdp;

import java.util.Objects;

import org.yamcs.filetransfer.FileTransferId;

public class CfdpTransactionId extends FileTransferId {
    private int sequenceNumber;

    public CfdpTransactionId(long entityId, long sequenceNumber) {
        super(entityId, sequenceNumber);
        this.sequenceNumber = (int) sequenceNumber;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
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
        CfdpTransactionId other = (CfdpTransactionId) o;
        return sequenceNumber == other.sequenceNumber && initiatorEntityId == other.initiatorEntityId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceNumber, initiatorEntityId);
    }
    
    @Override
    public String toString() {
        return initiatorEntityId + "_"+ sequenceNumber;
    }

}
