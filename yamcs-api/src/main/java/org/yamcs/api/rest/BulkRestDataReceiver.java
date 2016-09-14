package org.yamcs.api.rest;

import org.yamcs.api.YamcsApiException;

public interface BulkRestDataReceiver {
    /**
     * called when receiving data.
     * 
     * 
     * @param data
     * @throws YamcsApiException if exception is thrown the request will be aborted (connection to the server closed)
     */
    public void receiveData(byte[] data) throws YamcsApiException;
    public void receiveException(Throwable t);
}
