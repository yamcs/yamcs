package org.yamcs.simulation.simulator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlightDataHandler {

    private static final Logger log = LoggerFactory.getLogger(FlightDataHandler.class);

    private Vector<FlightData> entries = new Vector<>(1000, 500);
    private int currentEntry = 0;

    public FlightDataHandler() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                FlightDataHandler.class.getResourceAsStream("/landing_data/Flight parameters.csv")))) {
            String line;
            in.readLine(); // skip column titles

            while ((line = in.readLine()) != null) {
                line = line.replace(',', '.'); // compatible to decimals with comma (e.g. 1,23)
                String[] parts = line.split(";");
                FlightData entry = new FlightData();

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
        if (entries.isEmpty()) {
            return;
        }

        if (currentEntry >= entries.size()) {
            currentEntry = 0;
        }

        FlightData entry = entries.elementAt(currentEntry++);
        entry.fillPacket(packet, 0);
    }
}
