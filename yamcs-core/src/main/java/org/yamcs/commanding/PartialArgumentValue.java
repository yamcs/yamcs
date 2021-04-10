package org.yamcs.commanding;

import org.yamcs.xtce.Argument;
import org.yamcs.xtce.PathElement;

/**
 * Holds values related to members of aggregates or arrays
 * 
 * @author nm
 *
 */
public class PartialArgumentValue extends ArgumentValue {
    final PathElement[] path;

    public PartialArgumentValue(Argument def, PathElement[] path) {
        super(def);
        this.path = path;
    }

    /**
     * The path to the element of the aggregate or array for which the value applies
     * 
     * @return
     */
    public PathElement[] getPath() {
        return path;
    }

}
