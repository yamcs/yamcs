package org.yamcs.api.ws;
public class WSConstants {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MESSAGE_TYPE_REQUEST = 1;
    public static final int MESSAGE_TYPE_REPLY = 2;
    public static final int MESSAGE_TYPE_EXCEPTION = 3;
    public static final int MESSAGE_TYPE_DATA = 4;
    /**
     * When a request could not be parsed up until the request id, we return this request-id in the
     * exception response
     */
    public static final int NO_REQUEST_ID = -1;
}