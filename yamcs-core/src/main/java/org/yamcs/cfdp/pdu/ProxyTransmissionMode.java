package org.yamcs.cfdp.pdu;

public class ProxyTransmissionMode extends ReservedMessageToUser {
    public ProxyTransmissionMode(CfdpPacket.TransmissionMode transmissionMode) {
        super(MessageType.PROXY_TRANSMISSION_MODE, encode(transmissionMode));
    }

    private static byte[] encode(CfdpPacket.TransmissionMode transmissionMode) {
        return new byte[]{(byte) transmissionMode.getValue()};
    }
}
