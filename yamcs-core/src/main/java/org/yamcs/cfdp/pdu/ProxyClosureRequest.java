package org.yamcs.cfdp.pdu;

import org.yamcs.cfdp.CfdpUtils;

public class ProxyClosureRequest extends ReservedMessageToUser {
    private final boolean closureRequested;

    public ProxyClosureRequest(boolean closureRequested) {
        super(MessageType.PROXY_CLOSURE_REQUEST, encode(closureRequested));

        this.closureRequested = closureRequested;
    }

    public ProxyClosureRequest(byte[] content) {
        super(MessageType.PROXY_CLOSURE_REQUEST, content);

        this.closureRequested = (content[0] == 1);
    }

    private static byte[] encode(boolean closureRequested) {
        return new byte[]{ CfdpUtils.boolToByte(closureRequested, 7) };
    }

    public boolean isClosureRequested() {
        return closureRequested;
    }

    @Override
    public String toJson() {
        return "{type=" + getType() + ", length=" + getValue().length + ", messageType=" + MessageType.PROXY_TRANSMISSION_MODE
                + ", closureRequested=" + closureRequested + "}";
    }

    @Override
    public String toString() {
        return "ProxyClosureRequest(" + closureRequested + ")";
    }
}
