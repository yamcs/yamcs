package org.yamcs.simulation.simulator;

public enum BatteryCommand {
    BATTERY1_ON(1, true),
    BATTERY1_OFF(1, false),
    BATTERY2_ON(2, true),
    BATTERY2_OFF(2, false),
    BATTERY3_ON(3, true),
    BATTERY3_OFF(3, false);

    int batteryNumber;
    boolean batteryOn;

    private BatteryCommand(int batteryNumber, boolean batteryOn) {
        this.batteryNumber = batteryNumber;
        this.batteryOn = batteryOn;
    }
}
