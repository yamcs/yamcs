package org.yamcs.simulation.simulator.cfdp;

public class MessageToUser {
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
}
