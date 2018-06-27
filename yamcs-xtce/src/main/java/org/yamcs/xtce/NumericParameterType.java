package org.yamcs.xtce;

import org.yamcs.utils.DoubleRange;

public interface NumericParameterType extends ParameterType {
    
    /**
     * Adds a new, or unions with an existing range for the specified context and level
     * @param contextMatch use <tt>null</tt> for the default context
     */
    public void addAlarmRange(MatchCriteria contextMatch, DoubleRange floatRange, AlarmLevels level);
    
    /**
     * Creates (if not already existing) and gets the alarm associated to the context.
     * 
     * @param contextMatch use <tt>null</tt> for the default context
     * @return
     */
    public NumericAlarm createOrGetAlarm(MatchCriteria contextMatch);

    
    public DataEncoding getEncoding();
    
}
