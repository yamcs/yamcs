/**
 * 
 */
package org.yamcs.xtce;

/**
 * Context alarms are applied when the ContextMatch is true.  
 * Context alarms override Default alarms meaning that if the condition matches, this alarm applies and if the condition 
 *  does not match, then the defaultAlarm applies.
 * @author nm
 *
 */
public class EnumerationContextAlarm  extends EnumerationAlarm {
	
	/**
     * 
     */
    private static final long serialVersionUID = 201103300451L;
    private MatchCriteria contextMatch;
	
	public MatchCriteria getContextMatch() {
	    return contextMatch;
	}
	
    @Override
    public String toString() {
		return "EnumerationContextAlarm(contextMatch:"+getContextMatch()+", defaultLevel:"+defaultAlarmLevel+", alarmList: "+getAlarmList()+")";
	}

    public void setContextMatch(MatchCriteria contextMatch) {
        this.contextMatch = contextMatch;
    }
	
	
}
