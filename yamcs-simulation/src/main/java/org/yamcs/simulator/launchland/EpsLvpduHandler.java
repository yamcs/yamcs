package org.yamcs.simulator.launchland;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.simulator.CCSDSPacket;
import org.yamcs.simulator.SimulationConfiguration;

public class EpsLvpduHandler {

    private final static String csvName = "ESPLVPDU.csv";

    private Vector<EpsLvpduData> entries;
    private int currentEntry = 0;

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public EpsLvpduHandler(SimulationConfiguration simconf) {
        entries = new Vector<>(100, 100);
        try (BufferedReader in = new BufferedReader(new FileReader(simconf.getTestDataDir() + "/" + csvName))) {
            String line;
            in.readLine(); // skip column titles

            while ((line = in.readLine()) != null) {

                line = line.replace(',', '.'); // compatible to decimals with comma (e.g. 1,23)
                String[] parts = line.split(";");

                EpsLvpduData entry = new EpsLvpduData();

                entry.LVPDUStatus = new Integer(parts[0]).intValue();
                entry.LVPDUVoltage = new Float(parts[1]).floatValue();

                entries.add(entry);
            }
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
        log.info("have {} EPS LVPDU data records", entries.size());
    }

    public void fillPacket(CCSDSPacket packet) {
        if (entries.isEmpty())
            return;

        if (currentEntry >= entries.size()) {
            currentEntry = 0;
        }

        EpsLvpduData entry = entries.elementAt(currentEntry++);
        entry.fillPacket(packet, 0);
    }
}
