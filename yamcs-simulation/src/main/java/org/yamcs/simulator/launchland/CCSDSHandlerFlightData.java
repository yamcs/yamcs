package org.yamcs.simulator.launchland;

import org.yamcs.simulator.CCSDSPacket;

public class CCSDSHandlerFlightData extends CSVHandlerFlightData {
    private int currentEntry = 0;

    public CCSDSHandlerFlightData() {
        super(null);
    }

    public void fillPacket(CCSDSPacket packet) {
        if (getNumberOfEntries() == 0)
            return;

        for (int i = 0; i < 1; ++i) {
            if (currentEntry >= getNumberOfEntries()) {
                currentEntry = 0;
            }

            FlightData entry = entries.elementAt(currentEntry++);
            entry.fillPacket(packet, i * 60);
        }
    }
}
