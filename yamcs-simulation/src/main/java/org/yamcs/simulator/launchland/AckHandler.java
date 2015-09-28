package org.yamcs.simulator.launchland;

import org.yamcs.simulator.CCSDSPacket;

public class AckHandler {

    public void fillAckPacket(CCSDSPacket packet, int commandReceived) {
        AckData entry = new AckData();
        entry.fillPacket(packet, 0, commandReceived);
    }

    public void fillExeCompPacket(CCSDSPacket packet, int battery, int commandReceived) {
        AckData entry = new AckData();

        if (1 <= battery && battery <= 3) {
            entry.fillPacket(packet, battery - 1, commandReceived);
        }
    }
}
