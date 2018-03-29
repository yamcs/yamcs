package org.yamcs.parameterarchive;

import org.yamcs.protobuf.Yamcs.Value.Type;

/**
 * 
 * The parameter archive gives each (parameterFqn, rawType, engType) a numeric 32bits pid.
 * 
 * This class stores the association between pid, rawType and engType 
 *  
 * @author nm
 *
 */
public class ParameterId {
    public final int pid;
    public final Type engType;
    private final Type rawType;

    public ParameterId(int pid, int numericType) {
        this.pid = pid;
        this.engType = ParameterIdDb.getEngType(numericType);
        this.rawType = ParameterIdDb.getRawType(numericType);
    }

    @Override
    public String toString() {
        return "ParameterId [pid=" + pid + ", engType=" + engType
                + ", rawType=" + rawType + "]";
    }

    public Type getRawType() {
        return rawType;
    }
}