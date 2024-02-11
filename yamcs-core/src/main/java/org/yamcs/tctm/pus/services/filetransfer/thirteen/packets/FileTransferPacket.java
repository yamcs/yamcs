package org.yamcs.tctm.pus.services.filetransfer.thirteen.packets;

import org.yamcs.tctm.pus.services.filetransfer.thirteen.S13TransactionId;

public abstract class FileTransferPacket {
    protected S13TransactionId transactionId;

    FileTransferPacket(S13TransactionId transactionId){
        this.transactionId = transactionId;
    }

    public S13TransactionId getTransactionId() {
        return transactionId;
    }
}
