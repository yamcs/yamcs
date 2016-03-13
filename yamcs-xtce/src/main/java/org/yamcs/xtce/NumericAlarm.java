package org.yamcs.xtce;

import java.io.Serializable;

/**
 * Alarms associated with numeric data types
 * @author nm
 *
 */
public class NumericAlarm extends AlarmType implements Serializable {
    private static final long serialVersionUID = 200706052351L;

    /**
     * StaticAlarmRanges are used to trigger alarms when the parameter value
     * passes some threshold value (as opposed to delta alarms or other fancy alarms not supported by yamcs). 
     */
    private AlarmRanges staticAlarmRanges=new AlarmRanges();

   /**
    *  ChangeAlarmRanges are used to trigger alarms when the parameter value's rate-of-change is either too fast or too slow.
    *   The change may be with respect to time (the default) or with respect to samples (delta alarms) 
    *   - the changeType attribute determines this.  
    *   The change may also be either relative (as a percentage change) or absolute as set by the changeBasis attribute. 
    *    The alarm also requires the spanOfInterest in both samples and seconds to have passed before it is to trigger.  
    *    For time based rate of change alarms, the time specified in spanOfInterestInSeconds is used to calculate the change. 
    *    For sample based rate of change alarms, the change is calculated over the number of samples specified in spanOfInterestInSeconds.
    */
    private AlarmRanges changeAlarmRanges = null;
   
    
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
