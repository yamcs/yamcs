package org.yamcs.parameterarchive;

import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.IntArray;

/**
 * 
 * The parameter archive gives each (parameterFqn, rawType, engType) a numeric 32bits pid.
 * 
 * This class stores the association between pid, rawType and engType
 * 
 */
public interface ParameterId {

    public Type getRawType();

    public Type getEngType();

    public int getPid();

    public String getParamFqn();

    /**
     * @return true if the parameter id is not an aggregate or array
     */
    public boolean isSimple();

    /**
     * 
     * @return true if the parameter has a raw value. It is equivalent with getRawType()==null
     */
    public boolean hasRawValue();

    /**
     * Rreturns the ids of the components for aggregates or arrays. if isSimple() returns true, this method returns
     * null.
     */
    public IntArray getComponents();
}
