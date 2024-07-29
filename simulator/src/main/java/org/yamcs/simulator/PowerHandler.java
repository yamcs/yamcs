package org.yamcs.simulator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PowerHandler {

    private static final Logger log = LoggerFactory.getLogger(PowerHandler.class);

    private List<PowerData> entries = new ArrayList<>(100);
    private int currentEntry = 0;
    boolean batOnOff[] = { false, true, true, true }; // battery number is from 1 to 3, [0] is not used

    public PowerHandler() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(PowerHandler.class.getResourceAsStream("/landing_data/power.csv")))) {
            String line;
            in.readLine(); // skip column titles
            while ((line = in.readLine()) != null) {
                line = line.replace(',', '.'); // compatible to decimals with comma (e.g. 1,23)
                String[] parts = line.split(";");

                PowerData entry = new PowerData();

                entry.timestamp = Float.parseFloat(parts[0]);
                entry.busStatus = Integer.parseInt(parts[1]);
                entry.busVoltage = Float.parseFloat(parts[2]);
                entry.busCurrent = Float.parseFloat(parts[3]);
                entry.systemCurrent = Float.parseFloat(parts[4]);

                entry.batteryVoltage1 = Float.parseFloat(parts[5]);
                entry.batteryTemp1 = Float.parseFloat(parts[6]);
                entry.batteryCapacity1 = Float.parseFloat(parts[7]);

                entry.batteryVoltage2 = Float.parseFloat(parts[8]);
                entry.batteryTemp2 = Float.parseFloat(parts[9]);
                entry.batteryCapacity2 = Float.parseFloat(parts[10]);

                entry.batteryVoltage3 = Float.parseFloat(parts[11]);
                entry.batteryTemp3 = Float.parseFloat(parts[12]);
                entry.batteryCapacity3 = Float.parseFloat(parts[13]);

                entries.add(entry);
            }
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
        log.debug("have {} power data records", entries.size());
    }

    public void fillPacket(ByteBuffer buffer) {
        if (entries.isEmpty()) {
            return;
        }

        if (currentEntry >= entries.size()) {
            currentEntry = 0;
        }

        PowerData entry = entries.get(currentEntry++);
        entry.fillPacket(buffer);

        for (int i = 1; i < 4; i++) {
            if (!batOnOff[i]) {
                buffer.put(4 * i, (byte) 0);
            }
        }

    }

    public void setBatteryOn(int batNum) {
        batOnOff[batNum] = true;
    }

    public void setBatteryOff(int batNum) {
        batOnOff[batNum] = false;
    }

    public float getBattery1Voltage() {
        PowerData data = entries.get(currentEntry);
        return data.batteryVoltage1;
    }

    public float getBattery2Voltage() {
        PowerData data = entries.get(currentEntry);
        return data.batteryVoltage2;
    }

    public float getBattery3Voltage() {
        PowerData data = entries.get(currentEntry);
        return data.batteryVoltage3;
    }

    public int dataSize() {
        return PowerData.size();
    }
}
