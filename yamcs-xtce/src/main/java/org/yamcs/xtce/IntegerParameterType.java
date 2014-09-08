package org.yamcs.xtce;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IntegerParameterType extends IntegerDataType implements ParameterType {
	private static final long serialVersionUID=200806041255L;
	ArrayList<NumericContextAlarm> contextAlarmList=null;
	
	NumericAlarm defaultAlarm=null;
	
	public IntegerParameterType(String name){
		super(name);
	}
	
	public NumericAlarm createOrGetAlarm(MatchCriteria contextMatch) {
        if(contextMatch==null) {
            if(defaultAlarm==null) {
                defaultAlarm=new NumericAlarm();
            }
            return defaultAlarm;
        } else {
            NumericContextAlarm nca=getNumericContextAlarm(contextMatch);
            if(nca==null) {
                nca=new NumericContextAlarm();
                nca.setContextMatch(contextMatch);
                addContextAlarm(nca);
            }
            return nca;
        }
	}
	
	private AlarmRanges getAlarmRanges(MatchCriteria contextMatch) {
	    NumericAlarm alarm=createOrGetAlarm(contextMatch);
	    return alarm.getStaticAlarmRanges();
	}
	
	public void setDefaultWatchAlarmRange(FloatRange watchRange) {
	    getAlarmRanges(null).watchRange=watchRange;
	}
	
	public void setDefaultWarningAlarmRange(FloatRange warningRange) {
	    getAlarmRanges(null).warningRange=warningRange;
	}
	
	public void setDefaultDistressAlarmRange(FloatRange distressRange) {
	    getAlarmRanges(null).distressRange=distressRange;
	}
	
    public void setDefaultCriticalAlarmRange(FloatRange criticalRange) {
        getAlarmRanges(null).criticalRange=criticalRange;
    }
    
    public void setDefaultSevereAlarmRange(FloatRange severeRange) {
        getAlarmRanges(null).severeRange=severeRange;
    }

    /**
     * Adds a new, or unions with an existing range for the specified context and level
     * @param contextMatch use <tt>null</tt> for the default context
     */
    public void addAlarmRange(MatchCriteria contextMatch, FloatRange range, AlarmLevels level) {
        getAlarmRanges(contextMatch).addRange(range, level);
    }
	/**
	 * Adds a new, or unions with an existing watch range for the specified context
	 * @param contextMatch use <tt>null</tt> for the default context
	 */
	public void addWatchAlarmRange(MatchCriteria contextMatch, FloatRange watchRange) {
	    getAlarmRanges(contextMatch).addWatchRange(watchRange);
	}
	
    /**
     * Adds a new, or unions with an existing warning range for the specified context
     * @param contextMatch use <tt>null</tt> for the default context
     */
    public void addWarningAlarmRange(MatchCriteria contextMatch, FloatRange warningRange) {
        getAlarmRanges(contextMatch).addWarningRange(warningRange);
    }
    
    /**
     * Adds a new, or unions with an existing distress range for the specified context
     * @param contextMatch use <tt>null</tt> for the default context
     */
    public void addDistressAlarmRange(MatchCriteria contextMatch, FloatRange distressRange) {
        getAlarmRanges(contextMatch).addDistressRange(distressRange);
    }
    
    /**
     * Adds a new, or unions with an existing critical range for the specified context
     * @param contextMatch use <tt>null</tt> for the default context
     */
    public void addCriticalAlarmRange(MatchCriteria contextMatch, FloatRange criticalRange) {
        getAlarmRanges(contextMatch).addCriticalRange(criticalRange);
    }
    
    /**
     * Adds a new, or unions with an existing severe range for the specified context
     * @param contextMatch use <tt>null</tt> for the default context
     */
    public void addSevereAlarmRange(MatchCriteria contextMatch, FloatRange severeRange) {
        getAlarmRanges(contextMatch).addSevereRange(severeRange);
    }
	
	public void addContextAlarm(NumericContextAlarm nca) {
		if(contextAlarmList==null) contextAlarmList=new ArrayList<NumericContextAlarm>();
		contextAlarmList.add(nca);
	}

	public void addContextAlarms(Collection<NumericContextAlarm> ncas) {
		if(contextAlarmList==null) contextAlarmList=new ArrayList<NumericContextAlarm>();
		contextAlarmList.addAll(ncas);
	}

	public NumericAlarm getDefaultAlarm() {
		return defaultAlarm;
	}
	
	public NumericContextAlarm getNumericContextAlarm(MatchCriteria context) {
	    if(contextAlarmList==null) return null;
	    for(NumericContextAlarm nca:contextAlarmList) {
	        if(nca.getContextMatch().equals(context)) {
	            return nca;
	        }
	    }
	    return null;
	}

	public List<NumericContextAlarm> getContextAlarmList() {
	    return contextAlarmList;
	}
	
	@Override
	public boolean hasAlarm() {
	    return defaultAlarm!=null || (contextAlarmList!=null && !contextAlarmList.isEmpty());
	}
    
	@Override
    public Set<Parameter> getDependentParameters() {
        if(getContextAlarmList()==null)
            return null;
        Set<Parameter>dependentParameters=new HashSet<Parameter>();
        for(NumericContextAlarm nca:contextAlarmList)
            dependentParameters.addAll(nca.getContextMatch().getDependentParameters());
        return dependentParameters;
	}
		
	@Override
    public String toString() {
		return "IntegerDataType name:"+name+" sizeInBits:"+sizeInBits+" signed:"+signed+" encoding:"+encoding+((defaultAlarm!=null)?defaultAlarm:"");
	}

    @Override
    public String getTypeAsString() {
        return "integer";
    }
}
