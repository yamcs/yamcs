package org.yamcs.cfdp.pdu;

public class PduDecodingException extends RuntimeException {
    byte[] pdu;

    public PduDecodingException(String msg, byte[] pdu) {
        super(msg);
    }

    public PduDecodingException(String msg, byte[] pdu, Throwable t) {
        super(msg, t);
    }

    public byte[] getData() {
        return pdu;
    }

}
