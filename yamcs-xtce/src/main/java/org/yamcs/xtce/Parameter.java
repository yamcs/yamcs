package org.yamcs.xtce;

/**
 * A Parameter is a description of something that can have a value; it is not the value itself.
 */
public class Parameter extends NameDescription {
    private static final long serialVersionUID = 3L;
    ParameterType parameterType;
    DataSource dataSource = DataSource.TELEMETERED;
    /**
     * XTCE: A Parameter marked to persist should retain the latest value through resets/restarts to the extent that is
     * possible or defined in the implementation. The net effect is that the initial/default value on a Parameter is
     * only seen once or when the system has a reset to revert to initial/default values.
     */
    private boolean persistent;

    private Object initialValue;

    /**
     * This is used for recording; if the recordingGroup is not set, the subsystem name is used. Currently it is only
     * set for DaSS processed parameters for compatibility with the old recorder
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
        if (recordingGroup == null) {
            return getSubsystemName();
        } else {
            return recordingGroup;
        }
    }

    public void setRecordingGroup(String g) {
        this.recordingGroup = g;
    }

    /**
     * 
     * @return the initial value of the parameter (if any)
     */
    public Object getInitialValue() {
        return initialValue;
    }

    /**
     * Sets the initial value for the parameter (if any). The value has to be compatible with its type.
     * 
     * @param initialValue
     */
    public void setInitialValue(Object initialValue) {
        this.initialValue = initialValue;
    }

    /**
     * Return true if this parameter is used/valid in a commanding context: that is if the data source is
     * {@link DataSource#COMMAND} or {@link DataSource#COMMAND_HISTORY}
     * 
     */
    public boolean isCommandParameter() {
        return dataSource == DataSource.COMMAND || dataSource == DataSource.COMMAND_HISTORY;
    }

    @Override
    public String toString() {
        return "ParaName: " + this.getName() + " paraType:" + parameterType
                + ((xtceAliasSet == null) ? "" : " aliases: " + xtceAliasSet.toString());
    }

    public boolean isPersistent() {
        return persistent;
    }

    /**
     * If set, the parameter's value will be preserved during Yamcs restarts
     */
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

}
