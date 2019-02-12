package org.yamcs.simulation.simulator.cfdp;

public class MessageToUser {

    public static byte TYPE = 0x02;

    private byte[] message;

    public MessageToUser(byte[] message) {
        this.message = message;
    }

    public byte[] getMessage() {
        return this.message;
    }

    public static MessageToUser fromTLV(TLV tlv) {
        return new MessageToUser(tlv.getValue());
    }

    public TLV toTLV() {
        return new TLV(MessageToUser.TYPE, getMessage());
    }
}
