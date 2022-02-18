package org.yamcs.xtce;

/**
 * An enumerated list of the possible alarm levels
 * @author nm
 *
 */
public enum AlarmLevels {
    NORMAL("normal"),
    WATCH("watch"),
    WARNING("warning"),
    DISTRESS("distress"),
    CRITICAL("critical"),
    SEVERE("severe");

    public final String xtceName;

    private AlarmLevels(String xtceName) {
        this.xtceName = xtceName;
    }

    public static AlarmLevels fromXtce(String name) {
        for (AlarmLevels l : AlarmLevels.values()) {
            if (l.xtceName.equals(name)) {
                return l;
            }
        }
        throw new IllegalArgumentException("Illegal xtce name " + name);
    }
}
