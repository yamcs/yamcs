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

public class DHSHandler {
    private static final Logger log = LoggerFactory.getLogger(DHSHandler.class);

    private List<DHSData> entries = new ArrayList<>(100);
    private int currentEntry = 0;

    public DHSHandler() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(DHSHandler.class.getResourceAsStream("/landing_data/DHS.csv")))) {
            String line;
            line = in.readLine(); // skip column titles
            if (line == null) {
                throw new ConfigurationException("Empty DHS.csv file");
            }

            while ((line = in.readLine()) != null) {
                line = line.replace(',', '.'); // compatible to decimals with comma (e.g. 1,23)
                String[] parts = line.split(";");
                DHSData entry = new DHSData();

                entry.timestamp = Float.parseFloat(parts[0]);
                entry.primBusVoltage1 = Float.parseFloat(parts[1]);
                entry.primBusCurrent1 = Float.parseFloat(parts[2]);
                entry.primBusVoltage2 = Float.parseFloat(parts[3]);
                entry.primBusCurrent2 = Float.parseFloat(parts[4]);
                entry.secBusVoltage2 = Float.parseFloat(parts[5]);
                entry.secBusCurrent2 = Float.parseFloat(parts[6]);
                entry.secBusVoltage3 = Float.parseFloat(parts[7]);
                entry.secBusCurrent3 = Float.parseFloat(parts[8]);

                entries.add(entry);
            }
        } catch (IOException e) {
            System.out.println(e);
        }
        log.debug("have {} DHS data records", entries.size());
    }

    public void fillPacket(ByteBuffer buffer) {
        if (entries.isEmpty()) {
            return;
        }

        if (currentEntry >= entries.size()) {
            currentEntry = 0;
        }

        DHSData entry = entries.get(currentEntry++);
        entry.fillPacket(buffer);
    }

    public int dataSize() {
        return DHSData.size();
    }
}
