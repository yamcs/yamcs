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
	
	public List<EnumerationContextAlarm> getContextAlarmList() {
	    return contextAlarmList;
	}
	/**
	 * Extracts an enumerated value
	 * @param bb 
	 * @param locationInContainerInBits 
	 * @return the parameter value has the stateCodeValue set
	 */
	

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
