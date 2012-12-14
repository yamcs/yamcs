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

	public void setDefaultCriticalAlarmRange(FloatRange criticalRange) {
		if(getDefaultAlarm()==null)
			setDefaultAlarm(new NumericAlarm());
		getDefaultAlarm().getStaticAlarmRanges().criticalRange=criticalRange;
	}
 
	public void setDefaultWarningAlarmRange(FloatRange warningRange) {
		if(getDefaultAlarm()==null)
			setDefaultAlarm(new NumericAlarm());
		getDefaultAlarm().getStaticAlarmRanges().warningRange=warningRange;
	}

	public NumericAlarm getDefaultAlarm() {
		return defaultAlarm;
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
    public Set<Parameter> getDependentParameters() {
		if(getContextAlarmList()==null)
			return null;
		Set<Parameter>dependentParameters=new HashSet<Parameter>();
		for(NumericContextAlarm nca:contextAlarmList)
			dependentParameters.addAll(nca.getContextMatch().getDependentParameters());
		return dependentParameters;
	}


    public ArrayList<NumericContextAlarm> getContextAlarmList() {
        return contextAlarmList;
    }
    public void setDefaultAlarm(NumericAlarm defaultAlarm) {
        this.defaultAlarm = defaultAlarm;
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
