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

public class EpsLvpduHandler {

    private static final Logger log = LoggerFactory.getLogger(EpsLvpduHandler.class);

    private List<EpsLvpduData> entries = new ArrayList<>(100);
    private int currentEntry = 0;

    public EpsLvpduHandler() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(EpsLvpduHandler.class.getResourceAsStream("/landing_data/ESPLVPDU.csv")))) {
            String line;
            line = in.readLine(); // skip column titles
            if (line == null) {
                throw new ConfigurationException("Empty ESPLVPDU.csv file");
            }

            while ((line = in.readLine()) != null) {

                line = line.replace(',', '.'); // compatible to decimals with comma (e.g. 1,23)
                String[] parts = line.split(";");

                EpsLvpduData entry = new EpsLvpduData();

                entry.LVPDUStatus = Integer.parseInt(parts[0]);
                entry.LVPDUVoltage = Float.parseFloat(parts[1]);

                entries.add(entry);
            }
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
        log.debug("have {} EPS LVPDU data records", entries.size());
    }

    public void fillPacket(ByteBuffer buffer) {
        if (entries.isEmpty()) {
            return;
        }

        if (currentEntry >= entries.size()) {
            currentEntry = 0;
        }

        EpsLvpduData entry = entries.get(currentEntry++);
        entry.fillPacket(buffer);
    }

    public int dataSize() {
        return EpsLvpduData.size();
    }
}
