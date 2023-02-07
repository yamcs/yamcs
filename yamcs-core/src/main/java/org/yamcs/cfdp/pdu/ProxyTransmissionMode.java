package org.yamcs.cfdp.pdu;

public class ProxyTransmissionMode extends ReservedMessageToUser {
    private CfdpPacket.TransmissionMode transmissionMode;

    public ProxyTransmissionMode(CfdpPacket.TransmissionMode transmissionMode) {
        super(MessageType.PROXY_TRANSMISSION_MODE, encode(transmissionMode));

        this.transmissionMode = transmissionMode;
    }

    public ProxyTransmissionMode(byte[] content) {
        super(MessageType.PROXY_TRANSMISSION_MODE, content);

        this.transmissionMode = CfdpPacket.TransmissionMode.fromValue(content[0]);
    }

    private static byte[] encode(CfdpPacket.TransmissionMode transmissionMode) {
        return new byte[]{(byte) transmissionMode.getValue()};
    }

    public CfdpPacket.TransmissionMode getTransmissionMode() {
        return transmissionMode;
    }

    @Override
    public String toJson() {
        return "{type=" + getType() + ", length=" + getValue().length + ", messageType=" + MessageType.PROXY_TRANSMISSION_MODE
                + ", transmissionMode=" + transmissionMode + "}";
    }

    @Override
    public String toString() {
        return "ProxyTransmissionMode(" + transmissionMode + ")";
    }
}
