package org.yamcs.xtce;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Alarm conditions for Enumerations
 * An additional check needs to be performed to ensure that the enumeration values in the alarms are legal
 * enumeration values for the Parameter
 * 
 * @author nm
 *
 */
public class EnumerationAlarm extends AlarmType implements Serializable {
    private static final long serialVersionUID = 200707121420L;

    private List<EnumerationAlarmItem> alarmList = new ArrayList<EnumerationAlarmItem>();
    /**
     * If none from the list above applies, then this one is used
     */
    AlarmLevels defaultAlarmLevel = AlarmLevels.NORMAL;

    public void addAlarm(String label, AlarmLevels level) {
        alarmList.add(new EnumerationAlarmItem(label, level));
    }

    public AlarmLevels getDefaultAlarmLevel() {
        return defaultAlarmLevel;
    }

    public List<EnumerationAlarmItem> getAlarmList() {
        return alarmList;
    }

    public void setAlarmList(List<EnumerationAlarmItem> alarmList) {
        this.alarmList = alarmList;
    }

    @Override
    public String toString() {
        return "EnumerationAlarm(defaultLevel:" + defaultAlarmLevel + ", alarmList: " + alarmList;
    }

    static public class EnumerationAlarmItem implements Serializable {
        private static final long serialVersionUID = 200707121420L;

        AlarmLevels alarmLevel;
        String enumerationLabel;

        public EnumerationAlarmItem(String label, AlarmLevels level) {
            this.enumerationLabel = label;
            this.alarmLevel = level;
        }

        public String getEnumerationLabel() {
            return enumerationLabel;
        }

        public AlarmLevels getAlarmLevel() {
            return alarmLevel;
        }

        @Override
        public String toString() {
            return "(" + enumerationLabel + "->" + alarmLevel + ")";
        }
    }

    public void setDefaultAlarmLevel(AlarmLevels level) {
        this.defaultAlarmLevel = level;
    }
}
