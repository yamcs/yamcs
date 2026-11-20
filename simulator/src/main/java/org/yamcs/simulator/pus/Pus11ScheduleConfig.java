package org.yamcs.simulator.pus;

import org.yamcs.ConfigurationException;

/** Wire widths and defaults for scheduling group and sub-schedule identifiers. */
public record Pus11ScheduleConfig(PusTimeEncoding tcTime, int subScheduleBytes, long defaultSubSchedule,
        int groupBytes, long defaultGroup) {
    public Pus11ScheduleConfig {
        checkIdentifier("subScheduleId", subScheduleBytes, defaultSubSchedule);
        checkIdentifier("groupId", groupBytes, defaultGroup);
    }

    public Pus11ScheduleConfig(PusTimeEncoding tcTime) {
        this(tcTime, 1, 0, 1, 0);
    }

    private static void checkIdentifier(String name, int width, long value) {
        if (width < 1 || width > 8) {
            throw new ConfigurationException("PUS 11 " + name + " must be 1 to 8 bytes");
        }
        if (value < 0 || (width < 8 && value >= (1L << (width * 8)))) {
            throw new ConfigurationException("PUS 11 " + name + " default does not fit in " + width + " bytes");
        }
    }
}
