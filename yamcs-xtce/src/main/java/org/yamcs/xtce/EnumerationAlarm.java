package org.yamcs.xtce;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Alarm conditions for Enumerations</documentation
   An additional check needs to be performed to ensure that the enumeration values in the alarms are legal 
   enumeration values for the Parameter
 * @author nm
 *
 */
public class EnumerationAlarm implements Serializable{
	private static final long serialVersionUID=200707121420L;
	
	private List<EnumerationAlarmItem> alarmList=new ArrayList<EnumerationAlarmItem>();
	/**
	 * If none from the list above applies, then this one is used
	 */
	AlarmLevels defaultAlarmLevel=AlarmLevels.normal;
	
	

	public void addAlarm(String value, AlarmLevels level) {
		alarmList.add(new EnumerationAlarmItem(value,level));	
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
        EnumerationAlarmItem(String value, AlarmLevels level) {
            this.enumerationValue=value;
            this.alarmLevel=level;
        }
        AlarmLevels alarmLevel;
        String enumerationValue;
        @Override
        public String toString() {
            return "("+enumerationValue+"->"+alarmLevel+")";
        }
        
        public String getEnumerationValue() {
            return enumerationValue;
        }
        
        public AlarmLevels getAlarmLevel() {
            return alarmLevel;
        }
    }


    public void setDefaultAlarmLevel(AlarmLevels level) {
        this.defaultAlarmLevel=level;
    }
}
