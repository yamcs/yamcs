package org.yamcs.parameter;

import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.PathElement;

/**
 * Holds values related to members of aggregates or arrays 
 * 
 * @author nm
 *
 */
public class PartialParameterValue extends ParameterValue {
    final PathElement[] path;
    
    public PartialParameterValue(Parameter def, PathElement[] path) {
        super(def);
        this.path = path;
    }
    
    /**
     * The path to the element of the aggregate or array for which the value applies
     * @return
     */
    public PathElement[] getPath() {
        return path;
    }

}
