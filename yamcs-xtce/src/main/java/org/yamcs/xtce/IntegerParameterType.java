package org.yamcs.xtce;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IntegerParameterType extends IntegerDataType implements ParameterType {
	private static final long serialVersionUID=200806041255L;
	ArrayList<NumericContextAlarm> contextAlarmList=null;
	
	NumericAlarm defaultAlarm=null;
	
	public IntegerParameterType(String name){
		super(name);
	}

	public void setDefaultCriticalAlarmRange(FloatRange criticalRange) {
		if(defaultAlarm==null)
			defaultAlarm=new NumericAlarm();
		defaultAlarm.getStaticAlarmRanges().criticalRange=criticalRange;
	}
	
	public void setDefaultWarningAlarmRange(FloatRange warningRange) {
		if(defaultAlarm==null)
			defaultAlarm=new NumericAlarm();
		defaultAlarm.getStaticAlarmRanges().warningRange=warningRange;
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

	public List<NumericContextAlarm> getContextAlarmList() {
	    return contextAlarmList;
	}
    
	@Override
    public Set<Parameter> getDependentParameters() {
		return null;
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
