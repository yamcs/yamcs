package org.yamcs.pus;

import org.yamcs.utils.ByteArrayUtils;

/**
 * Some constants and utitlities related to the PUS packets
 */
public class PusPacket {
    public static final int TM_MIN_SIZE = 13;

    static final int SERVICE_TYPE_HK = 3;
    static final int SERVICE_TYPE_EVENT = 5;

    static int getApid(byte[] packet) {
        return ByteArrayUtils.decodeUnsignedShort(packet, 0) & 0x7FF;
    }

    static int getType(byte[] packet) {
        return packet[7] & 0xFF;
    }

    static int getSubtype(byte[] packet) {
        return packet[8] & 0xFF;
    }

}
