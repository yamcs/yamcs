package org.yamcs.cfdp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Only mapping a transaction ID to a download bucket at the moment
 */
public class FileDownloadRequests {

    private Map<CfdpTransactionId, String> buckets = new HashMap<>();

    public synchronized void addTransfer(CfdpTransactionId transactionId, String bucket) {
        buckets.put(transactionId, bucket);
    }

    public synchronized String removeTransfer(CfdpTransactionId transactionId) {
        return buckets.remove(transactionId);
    }

    public synchronized Map<CfdpTransactionId, String> getBuckets() {
        return Collections.unmodifiableMap(buckets);
    }
}
