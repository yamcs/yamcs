package org.yamcs.xtce;

/**
 * A Parameter is a description of something that can have a value; it is not the value itself.
 */
public class Parameter extends NameDescription {
    private static final long serialVersionUID = 2L;
    ParameterType parameterType;
    DataSource dataSource;
    
    /**
     * This is used for recording; if the recordingGroup is not set, the subsystem name is used.
     * Currently it is only set for DaSS processed parameters for compatibility with the old recorder
     */
    String recordingGroup = null;
    
    public Parameter(String name) {
        super(name);
    }

    public DataSource getDataSource() {
        return dataSource;
    }
    
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setParameterType(ParameterType pm) {
        parameterType = pm;
    }

    public ParameterType getParameterType() {
        return parameterType;
    }

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
    
    @Override
    public String toString() {
        return "ParaName: " + this.getName() + " paraType:" + parameterType +" opsname: "+getOpsName();
    }
}
