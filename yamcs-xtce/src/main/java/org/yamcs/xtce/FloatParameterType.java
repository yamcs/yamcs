package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class FloatParameterType extends FloatDataType implements ParameterType {
    private static final long serialVersionUID=200805131551L;
    private NumericAlarm defaultAlarm=null;
    private ArrayList<NumericContextAlarm> contextAlarmList=null;


    public FloatParameterType(String name){
        super(name);
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

    public NumericAlarm getDefaultAlarm() {
        return defaultAlarm;
    }

    /**
     * Adds a new, or unions with an existing range for the specified context and level
     * @param contextMatch use <tt>null</tt> for the default context
     */
    public void addAlarmRange(MatchCriteria contextMatch, FloatRange floatRange, AlarmLevels level) {
        getAlarmRanges(contextMatch).addRange(floatRange, level);

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

    public NumericContextAlarm getNumericContextAlarm(MatchCriteria context) {
        if(contextAlarmList==null) return null;
        for(NumericContextAlarm nca:contextAlarmList) {
            if(nca.getContextMatch().equals(context)) {
                return nca;
            }
        }
        return null;
    }

    public ArrayList<NumericContextAlarm> getContextAlarmList() {
        return contextAlarmList;
    }

    public void setDefaultAlarm(NumericAlarm defaultAlarm) {
        this.defaultAlarm = defaultAlarm;
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

    @Override
    public String getTypeAsString() {
        return "float";
    }

    @Override
    public String toString() {
        return "FloatParameterType name:"+name+" sizeInBits:"+sizeInBits+" encoding:"+encoding+((getDefaultAlarm()!=null)?"defaultAlarm:"+getDefaultAlarm():"")
                +((contextAlarmList!=null)?"contextAlarmList:"+contextAlarmList:"");
    }
}
