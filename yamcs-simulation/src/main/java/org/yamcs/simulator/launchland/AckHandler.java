package org.yamcs.simulator.launchland;

import java.nio.ByteBuffer;

import org.yamcs.simulator.CCSDSPacket;

public class AckHandler {

    public static void fillAckPacket(CCSDSPacket packet, int commandReceived) {
        fillPacket(packet, 0, commandReceived);
    }

    public static void fillExeCompPacket(CCSDSPacket packet, int battery, int commandReceived) {
        if (1 <= battery && battery <= 3) {
            fillPacket(packet, battery - 1, commandReceived);
        }
    }

    public static void fillPacket(CCSDSPacket packet, int bufferOffset, int commandReceived) {
        ByteBuffer buffer = packet.getUserDataBuffer();
        buffer.position(bufferOffset);
        buffer.put((byte) commandReceived);
    }
}
