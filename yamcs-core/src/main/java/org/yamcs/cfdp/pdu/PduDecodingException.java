package org.yamcs.cfdp.pdu;

public class PduDecodingException extends RuntimeException {
    final byte[] pdu;

    public PduDecodingException(String msg, byte[] pdu) {
        super(msg);
        this.pdu = pdu;
    }

    public PduDecodingException(String msg, byte[] pdu, Throwable t) {
        super(msg, t);
        this.pdu = pdu;
    }

    public byte[] getData() {
        return pdu;
    }
}
