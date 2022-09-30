package org.yamcs.cfdp.pdu;

public class MessageToUser extends TLV {
    public MessageToUser(byte[] value) {
        super(TLV.TYPE_MESSAGE_TO_USER, value);
    }
}
