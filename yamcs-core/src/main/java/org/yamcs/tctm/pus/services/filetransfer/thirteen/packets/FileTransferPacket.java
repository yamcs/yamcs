package org.yamcs.tctm.pus.services.filetransfer.thirteen.packets;

import org.yamcs.tctm.pus.services.filetransfer.thirteen.S13TransactionId.S13UniqueId;;

public abstract class FileTransferPacket {
    protected S13UniqueId uniquenessId;

    FileTransferPacket(S13UniqueId uniquenessId){
        this.uniquenessId = uniquenessId;
    }

    public S13UniqueId getUniquenessId() {
        return uniquenessId;
    }
}
