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
public class NumericContextAlarm extends NumericAlarm {
    private static final long serialVersionUID = 201103300437L;

    private MatchCriteria contextMatch;
	
    public MatchCriteria getContextMatch() {
        return contextMatch;
    }
  
    public void setContextMatch(MatchCriteria contextMatch) {
        this.contextMatch = contextMatch;
    }
    
    @Override
    public String toString() {
        return "NumericContextAlarm(contextMatch:"+getContextMatch()+", alarm:"+getStaticAlarmRanges()+", minViolations: "+getMinViolations()+")";
    }
}
