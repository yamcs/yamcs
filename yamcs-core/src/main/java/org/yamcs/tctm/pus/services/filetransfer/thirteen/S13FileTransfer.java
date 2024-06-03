package org.yamcs.tctm.pus.services.filetransfer.thirteen;

import org.yamcs.filetransfer.FileTransfer;
import org.yamcs.protobuf.TransferDirection;

public interface S13FileTransfer extends FileTransfer {
    enum PredefinedTransferTypes {
        DOWNLOAD_LARGE_FILE_TRANSFER("Download_Large_File_Transfer"),
        DOWNLOAD_REQUEST("Download_Request"),
        UPLOAD_LARGE_FILE_TRANSFER("Upload_Large_File_Transfer");

        private final String value;

        PredefinedTransferTypes(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value.toUpperCase();
        }
    }

    S13TransactionId getTransactionId();
    
    long getRemoteId();

    String getOrigin();

    long getInitiatorEntityId();

    long getDestinationId();

    @Override
    default Long getLocalEntityId() {
        if (getDirection() == TransferDirection.UPLOAD) {
            return getInitiatorEntityId();
        } else {
            return getDestinationId();
        }
    }

    @Override
    default Long getRemoteEntityId() {
        if (getDirection() == TransferDirection.UPLOAD) {
            return getDestinationId();
        } else {
            return getInitiatorEntityId();
        }
    }
}
