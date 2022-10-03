package org.yamcs.cfdp.pdu;

import org.yamcs.cfdp.CfdpUtils;

public class ProxyClosureRequest extends ReservedMessageToUser {
    public ProxyClosureRequest(boolean closureRequested) {
        super(MessageType.PROXY_CLOSURE_REQUEST, encode(closureRequested));
    }

    private static byte[] encode(boolean closureRequested) {
        return new byte[]{ CfdpUtils.boolToByte(closureRequested, 7) };
    }
}
