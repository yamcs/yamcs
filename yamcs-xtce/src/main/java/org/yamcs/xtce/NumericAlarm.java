package org.yamcs.xtce;

import java.io.Serializable;

/**
 * Alarms associated with numeric data types
 * @author nm
 *
 */
public class NumericAlarm implements Serializable {
	private static final long serialVersionUID = 200706052351L;
	
	/**
	 * StaticAlarmRanges are used to trigger alarms when the parameter value
	 * passes some threshold value (as opposed to delta alarms or other fancy alarms not supported by yamcs). 
	 */
	private AlarmRanges staticAlarmRanges=new AlarmRanges();
	
	
	public AlarmRanges getStaticAlarmRanges() {
		return staticAlarmRanges;
	}
	

    public void setStaticAlarmRanges(AlarmRanges staticAlarmRanges) {
        this.staticAlarmRanges = staticAlarmRanges;
    }
    
    @Override
    public String toString() {
        return getStaticAlarmRanges().toString();
    }
}
