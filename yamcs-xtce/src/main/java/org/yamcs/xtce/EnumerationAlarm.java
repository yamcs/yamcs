package org.yamcs.xtce;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Alarm conditions for Enumerations
 * An additional check needs to be performed to ensure that the enumeration values in the alarms are legal 
 * enumeration values for the Parameter
 * @author nm
 *
 */
public class EnumerationAlarm extends AlarmType implements Serializable{
    private static final long serialVersionUID=200707121420L;

    private List<EnumerationAlarmItem> alarmList=new ArrayList<EnumerationAlarmItem>();
    /**
     * If none from the list above applies, then this one is used
     */
    AlarmLevels defaultAlarmLevel=AlarmLevels.normal;



    public void addAlarm(ValueEnumeration enumValue, AlarmLevels level) {
        alarmList.add(new EnumerationAlarmItem(enumValue,level));	
    }

    public AlarmLevels getDefaultAlarmLevel() {
        return defaultAlarmLevel;
    }
    public List<EnumerationAlarmItem> getAlarmList() {
        return alarmList;
    }

    public void setAlarmList(List<EnumerationAlarmItem> alarmList) {
        this.alarmList=alarmList;
    }
    
    @Override
    public String toString() {
        return "EnumerationAlarm(defaultLevel:"+defaultAlarmLevel+", alarmList: "+alarmList;
    }

    static public class EnumerationAlarmItem implements Serializable {
        private static final long serialVersionUID = 200707121420L;
        EnumerationAlarmItem(ValueEnumeration enumValue, AlarmLevels level) {
            this.enumerationValue=enumValue;
            this.alarmLevel=level;
        }
        AlarmLevels alarmLevel;
        ValueEnumeration enumerationValue;
        
        

        public ValueEnumeration getEnumerationValue() {
            return enumerationValue;
        }

        public AlarmLevels getAlarmLevel() {
            return alarmLevel;
        }
        
        @Override
        public String toString() {
            return "("+enumerationValue.getLabel()+"->"+alarmLevel+")";
        }
    }


    public void setDefaultAlarmLevel(AlarmLevels level) {
        this.defaultAlarmLevel=level;
    }
}
