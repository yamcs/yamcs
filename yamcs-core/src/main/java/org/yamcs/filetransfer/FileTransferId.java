package org.yamcs.filetransfer;

public abstract class FileTransferId {
    protected long initiatorEntityId;
    protected long transferId;

    protected FileTransferId(long initiatorEntityId, long transferId) {
        this.initiatorEntityId = initiatorEntityId;
        this.transferId = transferId;
    }

    public long getInitiatorEntity() {
        return initiatorEntityId;
    }

    public long getTransferId() {
        return transferId;
    }

}
