package org.yamcs.simulator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;

public class RCSHandler {
    private static final Logger log = LoggerFactory.getLogger(RCSHandler.class);

    private List<RCSData> entries = new ArrayList<>(100);
    private int currentEntry = 0;

    public RCSHandler() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(RCSHandler.class.getResourceAsStream("/landing_data/RCS.csv")))) {
            String line;
            line = in.readLine(); // skip column titles
            if (line == null) {
                throw new ConfigurationException("Empty RCS.csv file");
            }

            while ((line = in.readLine()) != null) {
                line = line.replace(',', '.'); // compatible to decimals with comma (e.g. 1,23)
                String[] parts = line.split(";");
                RCSData entry = new RCSData();

                entry.timestamp = Float.parseFloat(parts[0]);

                entry.H2TankFill = Float.parseFloat(parts[1]);
                entry.H2TankTemp = Float.parseFloat(parts[2]);
                entry.H2TankPressure = Float.parseFloat(parts[3]);
                entry.H2ValveTemp = Float.parseFloat(parts[4]);
                entry.H2ValvePressure = Float.parseFloat(parts[5]);

                entry.O2TankFill = Float.parseFloat(parts[6]);
                entry.O2TankTemp = Float.parseFloat(parts[7]);
                entry.O2TankPressure = Float.parseFloat(parts[8]);
                entry.O2ValveTemp = Float.parseFloat(parts[9]);
                entry.O2ValvePressure = Float.parseFloat(parts[10]);

                entry.TurbineTemp = Float.parseFloat(parts[11]);
                entry.TurbinePressure = Float.parseFloat(parts[12]);

                entries.add(entry);
            }
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
        log.debug("have {} RHS data records", entries.size());
    }

    public void fillPacket(ByteBuffer buffer) {
        if (entries.isEmpty()) {
            return;
        }

        if (currentEntry >= entries.size()) {
            currentEntry = 0;
        }

        RCSData entry = entries.get(currentEntry++);
        entry.fillPacket(buffer);
    }

    public int dataSize() {
        return RCSData.size();
    }
}
