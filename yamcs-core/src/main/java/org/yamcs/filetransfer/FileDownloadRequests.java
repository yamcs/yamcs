package org.yamcs.filetransfer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Only mapping a transaction ID to a download bucket at the moment
 */
public class FileDownloadRequests {

    private Map<FileTransferId, String> buckets = new HashMap<>();

    public synchronized void addTransfer(FileTransferId transactionId, String bucket) {
        buckets.put(transactionId, bucket);
    }

    public synchronized String removeTransfer(FileTransferId transactionId) {
        return buckets.remove(transactionId);
    }

    public synchronized Map<FileTransferId, String> getBuckets() {
        return Collections.unmodifiableMap(buckets);
    }
}
