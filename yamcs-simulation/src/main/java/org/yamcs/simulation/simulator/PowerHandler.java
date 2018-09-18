package org.yamcs.simulation.simulator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PowerHandler {

    private static final Logger log = LoggerFactory.getLogger(PowerHandler.class);

    private Vector<PowerData> entries = new Vector<>(100, 100);
    private int currentEntry = 0;

    public PowerHandler() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(PowerHandler.class.getResourceAsStream("/landing_data/power.csv")))) {
            String line;
            in.readLine(); // skip column titles

            while ((line = in.readLine()) != null) {
                line = line.replace(',', '.'); // compatible to decimals with comma (e.g. 1,23)
                String[] parts = line.split(";");

                PowerData entry = new PowerData();

                entry.timestamp = new Float(parts[0]).floatValue();
                entry.busStatus = new Integer(parts[1]).intValue();
                entry.busVoltage = new Float(parts[2]).floatValue();
                entry.busCurrent = new Float(parts[3]).floatValue();
                entry.systemCurrent = new Float(parts[4]).floatValue();

                entry.batteryVoltage1 = new Float(parts[5]).floatValue();
                entry.batteryTemp1 = new Float(parts[6]).floatValue();
                entry.batteryCapacity1 = new Float(parts[7]).floatValue();

                entry.batteryVoltage2 = new Float(parts[8]).floatValue();
                entry.batteryTemp2 = new Float(parts[9]).floatValue();
                entry.batteryCapacity2 = new Float(parts[10]).floatValue();

                entry.batteryVoltage3 = new Float(parts[11]).floatValue();
                entry.batteryTemp3 = new Float(parts[12]).floatValue();
                // entry.batteryCapacity3 = new Float(parts[13]).floatValue();

                entries.add(entry);
            }
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
        log.info("have {} power data records", entries.size());
    }

    public void fillPacket(CCSDSPacket packet) {
        if (entries.isEmpty()) {
            return;
        }

        if (currentEntry >= entries.size()) {
            currentEntry = 0;
        }

        PowerData entry = entries.elementAt(currentEntry++);
        entry.fillPacket(packet, 0);
    }

    public void setBattOneOff(CCSDSPacket packet) {
        ByteBuffer buffer = packet.getUserDataBuffer();
        buffer.put(3, (byte) 0);
        buffer.put(4, (byte) 0);
        buffer.putShort(5, (short) 0);
    }

    public void setBattTwoOff(CCSDSPacket packet) {
        ByteBuffer buffer = packet.getUserDataBuffer();
        buffer.put(7, (byte) 0);
        buffer.put(8, (byte) 0);
        buffer.putShort(9, (short) 0);
    }

    public void setBattThreeOff(CCSDSPacket packet) {
        ByteBuffer buffer = packet.getUserDataBuffer();
        buffer.put(11, (byte) 0);
        buffer.put(12, (byte) 0);
        buffer.putShort(13, (short) 0);
    }

    public float getBattery1Voltage() {
        PowerData data = entries.elementAt(currentEntry);
        return data.batteryVoltage1;
    }

    public float getBattery2Voltage() {
        PowerData data = entries.elementAt(currentEntry);
        return data.batteryVoltage2;
    }

    public float getBattery3Voltage() {
        PowerData data = entries.elementAt(currentEntry);
        return data.batteryVoltage3;
    }

    public void displayUserData(CCSDSPacket packet) {
        ByteBuffer buffer = packet.getUserDataBuffer();
        buffer.position(16);

        for (int i = 0; i < buffer.capacity(); i++) {
            System.out.print(buffer.get(i) + " : ");
        }
        System.out.print(" \n ");
    }
}
