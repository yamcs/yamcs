package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class EnumeratedParameterType extends EnumeratedDataType implements ParameterType {
    private static final long serialVersionUID = 200805301432L;

    EnumerationAlarm defaultAlarm=null;
    ArrayList<EnumerationContextAlarm> contextAlarmList=null;

    public EnumeratedParameterType(String name){
        super(name);
    }

    @Override
    public boolean hasAlarm() {
        return defaultAlarm!=null || (contextAlarmList!=null && !contextAlarmList.isEmpty());
    }

    @Override
    public Set<Parameter> getDependentParameters() {
        if(contextAlarmList==null)
            return null;
        Set<Parameter>dependentParameters=new HashSet<Parameter>();
        for(EnumerationContextAlarm eca:contextAlarmList)
            dependentParameters.addAll(eca.getContextMatch().getDependentParameters());
        return dependentParameters;
    }

    public EnumerationAlarm getDefaultAlarm() {
        return defaultAlarm;
    }

    public void setDefaultAlarm(EnumerationAlarm enumerationAlarm) {
        this.defaultAlarm=enumerationAlarm;
    }	

    public void addContextAlarm(EnumerationContextAlarm nca) {
        if(contextAlarmList==null) contextAlarmList=new ArrayList<EnumerationContextAlarm>();
        contextAlarmList.add(nca);
    }

    public EnumerationContextAlarm getContextAlarm(MatchCriteria contextMatch) {
        if(contextAlarmList==null) return null;
        for(EnumerationContextAlarm eca:contextAlarmList) {
            if(eca.getContextMatch().equals(contextMatch)) {
                return eca;
            }
        }
        return null;
    }

    /**
     * Adds a new contextual alarm for the specified value
     * @param contextMatch use <tt>null</tt> for the default context
     */
    public void addAlarm(MatchCriteria contextMatch, ValueEnumeration enumValue, AlarmLevels level) {
        createOrGetAlarm(contextMatch).addAlarm(enumValue, level);
    }

    public EnumerationAlarm createOrGetAlarm(MatchCriteria contextMatch) {
        if(contextMatch==null) {
            if(defaultAlarm==null) {
                defaultAlarm=new EnumerationAlarm();
            }
            return defaultAlarm;
        } else {
            EnumerationContextAlarm eca=getContextAlarm(contextMatch);
            if(eca==null) {
                eca=new EnumerationContextAlarm();
                eca.setContextMatch(contextMatch);
                addContextAlarm(eca);
            }
            return eca;
        }
    }

    public List<EnumerationContextAlarm> getContextAlarmList() {
        return contextAlarmList;
    }	

    public String calibrate(long raw) {
        ValueEnumeration v = enumeration.get(raw);
        return v == null ? "UNDEF" : v.label;
    }

    public String getCalibrationDescription() {
        return "EnumeratedParameterType: "+enumeration;
    }

    @Override
    public String getTypeAsString() {
        return "enumeration";
    }

    @Override
    public String toString() {
        return "EnumeratedParameterType: "+enumeration+" encoding:"+encoding+((defaultAlarm!=null)?defaultAlarm:"")+((contextAlarmList!=null)?contextAlarmList:"");
    }
}
