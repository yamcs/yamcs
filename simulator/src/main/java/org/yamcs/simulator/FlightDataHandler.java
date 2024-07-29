package org.yamcs.simulator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlightDataHandler {

    private static final Logger log = LoggerFactory.getLogger(FlightDataHandler.class);

    private List<FlightData> entries = new ArrayList<>(1000);
    private int currentEntry = 0;

    public FlightDataHandler() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                FlightDataHandler.class.getResourceAsStream("/landing_data/Flight parameters.csv")))) {
            String line;
            line = in.readLine(); // skip column titles
            while ((line = in.readLine()) != null) {
                line = line.replace(',', '.'); // compatible to decimals with comma (e.g. 1,23)
                String[] parts = line.split(";");
                FlightData entry = new FlightData();

                entry.timestamp = Double.parseDouble(parts[0]);
                entry.longitude = Double.parseDouble(parts[1]);
                entry.latitude = Double.parseDouble(parts[2]);
                entry.altitude = Double.parseDouble(parts[3]);
                entry.heading = Float.parseFloat(parts[4]);
                entry.alpha = Float.parseFloat(parts[5]);
                entry.beta = Float.parseFloat(parts[6]);
                entry.tas = Float.parseFloat(parts[7]);
                entry.cas = Float.parseFloat(parts[8]);
                entry.mach = Float.parseFloat(parts[9]);
                entry.loadFactor = Float.parseFloat(parts[10]);
                entry.sinkRate = Float.parseFloat(parts[11]);
                entry.phi = Float.parseFloat(parts[12]);
                entry.theta = Float.parseFloat(parts[13]);
                entry.psi = Float.parseFloat(parts[14]);

                entries.add(entry);
            }
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
        log.debug("have {} flight data records", entries.size());
    }

    public void fillPacket(ByteBuffer buffer) {
        if (entries.isEmpty()) {
            return;
        }

        if (currentEntry >= entries.size()) {
            currentEntry = 0;
        }

        FlightData entry = entries.get(currentEntry++);
        entry.fillPacket(buffer);
    }

    public int dataSize() {
        return FlightData.size();
    }
}
