package org.yamcs.cfdp;

import org.yamcs.filetransfer.FileTransfer;

public interface CfdpFileTransfer extends FileTransfer {

    enum PredefinedTransferTypes {
        FILE_TRANSFER("File Transfer"),
        LARGE_FILE_TRANSFER("Large File Transfer"),
        DOWNLOAD_REQUEST("Download Request"),
        DOWNLOAD_REQUEST_RESPONSE("Download Request Response"),
        DIRECTORY_LISTING_REQUEST("Directory Listing Request"),
        DIRECTORY_LISTING_RESPONSE("Directory Listing Response"),
        ORIGINATING_TRANSACTION_ID_ONLY("Originating Transaction ID only"),
        UNSUPPORTED_METADATA_OPTIONS("Unsupported Metadata Options"),
        UNSUPPORTED_METADATA_MESSAGE("Unsupported Metadata Messages"),

        UNSUPPORTED_METADATA_RESERVED_MESSAGES("Unsupported Metadata Reserved Messages"), // Unused
        UNKNOWN_EMPTY_FILE("Unknown Empty File"),
        UNKNOWN("Unknown");

        private final String value;

        PredefinedTransferTypes(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Get the CFDP transaction id. Returns null for queued transfers.
     * 
     * @return
     */
    CfdpTransactionId getTransactionId();

    long getInitiatorEntityId();

    long getDestinationId();

}
