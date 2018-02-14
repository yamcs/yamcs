package org.yamcs.simulator.launchland;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.simulator.CCSDSPacket;
import org.yamcs.simulator.SimulationConfiguration;

class FlightDataHandler {
    private final static String csvName = "Flight parameters.csv";

    private Vector<FlightData> entries;
    private int currentEntry = 0;

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    public FlightDataHandler(SimulationConfiguration simconf) {
        entries = new Vector<>(1000, 500);
        try (BufferedReader in = new BufferedReader(new FileReader(simconf.getTestDataDir() + "/" + csvName))) {
            String line;
            in.readLine(); // skip column titles

            while ((line = in.readLine()) != null) {
                line = line.replace(',', '.'); // compatible to decimals with comma (e.g. 1,23)
                String[] parts = line.split(";");
                FlightData entry = new FlightData();

                // old "FlightData.csv"

                // entry.timestamp = new Float(parts[0]).floatValue();
                // entry.latitude = new Double(parts[1]).doubleValue();
                // entry.longitude = new Double(parts[2]).doubleValue();
                // entry.altitude = new Double(parts[3]).doubleValue();
                // entry.heading = new Float(parts[4]).floatValue();
                // entry.groundSpeed = new Float(parts[9]).floatValue();
                // entry.mach = new Float(parts[10]).floatValue();
                // entry.verticalSpeed = new Float(parts[14]).floatValue();
                // entry.phi = new Float(parts[15]).floatValue();
                // entry.theta = new Float(parts[16]).floatValue();
                // entry.psi = new Float(parts[17]).floatValue();

                // new "Datasheet_test_data.csv"

                entry.timestamp = new Double(parts[0]).doubleValue();
                entry.longitude = new Double(parts[1]).doubleValue();
                entry.latitude = new Double(parts[2]).doubleValue();
                entry.altitude = new Double(parts[3]).doubleValue();
                entry.heading = new Float(parts[4]).floatValue();
                entry.alpha = new Float(parts[5]).floatValue();
                entry.beta = new Float(parts[6]).floatValue();
                entry.tas = new Float(parts[7]).floatValue();
                entry.cas = new Float(parts[8]).floatValue();
                entry.mach = new Float(parts[9]).floatValue();
                entry.loadFactor = new Float(parts[10]).floatValue();
                entry.sinkRate = new Float(parts[11]).floatValue();
                entry.phi = new Float(parts[12]).floatValue();
                entry.theta = new Float(parts[13]).floatValue();
                entry.psi = new Float(parts[14]).floatValue();

                entries.add(entry);
            }
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
        log.info("have {} flight data records", entries.size());

    }

    public void fillPacket(CCSDSPacket packet) {
        if (entries.isEmpty())
            return;

        if (currentEntry >= entries.size()) {
            currentEntry = 0;
        }

        FlightData entry = entries.elementAt(currentEntry++);
        entry.fillPacket(packet, 0);
    }

}
