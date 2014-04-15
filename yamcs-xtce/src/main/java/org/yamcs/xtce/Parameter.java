package org.yamcs.xtce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * A Parameter is a description of something that can have a value; it is not the value itself.
 */
public class Parameter extends NameDescription {
	private static final long serialVersionUID = 201404150619L;
	ParameterType parameterType;
	
	String recordingGroup = null;
	
	static Logger log=LoggerFactory.getLogger(Parameter.class.getName());

	public Parameter(String name) {
		super(name);
	}
	
	
	public void setParameterType(ParameterType pm) {
		parameterType = pm;
	}

	public ParameterType getParameterType() {
		return parameterType;
	}
	
	@Override
    public String toString() {
        return "ParaName: " + this.getName() + " paraType:" + parameterType +" opsname: "+getOpsName();
    }


	/*
	 * This is used for recording; if the recordingGroup is not set, the subsystem name is used.
	 * Currently it is only set for DaSS processed parameters for compatibility with the old recorder
	 */
    public String getRecordingGroup() {
        if(recordingGroup == null) {
            return getSubsystemName();
        } else {
            return recordingGroup;
        }
    }
    
    public void setRecordingGroup(String g) {
        this.recordingGroup = g;
    }

}
