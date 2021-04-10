package org.yamcs.xtce;

import java.io.Serializable;

public abstract class ParameterOrArgumentRef implements Serializable {

    private static final long serialVersionUID = 1L;
    protected boolean useCalibratedValue = true;
    protected PathElement[] path;

    public boolean useCalibratedValue() {
        return useCalibratedValue;
    }

    public void setUseCalibratedValue(boolean useCalibratedValue) {
        this.useCalibratedValue = useCalibratedValue;
    }

    /**
     * If the parameter or argument is an aggregate or an array (or a nested structure of these), return the path
     * to the referenced member inside the structure.
     * 
     * @return the path to the referenced member of the aggregate or array or null if this reference refers to the
     *         parameter itself
     */
    public PathElement[] getMemberPath() {
        return path;
    }

    public void setMemberPath(PathElement[] path) {
        this.path = path;
    }

    public abstract String getName();

    public abstract DataType getDataType();

}
