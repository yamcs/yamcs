package org.yamcs.simulator.launchland;

import org.yamcs.simulator.CCSDSPacket;

public class CCSDSHandlerRCS extends CSVHandlerRCS {
    private int currentEntry = 0;

    public void fillPacket(CCSDSPacket packet) {
        if (getNumberOfEntries() == 0)
            return;

        if (currentEntry >= getNumberOfEntries()) {
            currentEntry = 0;
        }

        RCSData entry = entries.elementAt(currentEntry++);
        entry.fillPacket(packet, 0);
    }
}
