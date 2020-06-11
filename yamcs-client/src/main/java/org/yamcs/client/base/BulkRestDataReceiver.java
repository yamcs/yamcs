package org.yamcs.client.base;

import org.yamcs.client.ClientException;

public interface BulkRestDataReceiver {
    /**
     * called when receiving data.
     * 
     * 
     * @param data
     * @throws ClientException
     *             if exception is thrown the request will be aborted (connection to the server closed)
     */
    public void receiveData(byte[] data) throws ClientException;

    /**
     * Called when receiving an exception. Note that the CompleteableFuture returned from RestClient can also be used to
     * intercept the exception;
     * 
     * @param t
     */
    default void receiveException(Throwable t) {
    }
}
