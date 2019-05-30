package org.yamcs.parameter;

import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.PathElement;

/**
 * Holder class for a parameter together with the id what used to subscribe (or request) it.
 * 
 * The subscription/request can point to an aggregate/array element.
 * 
 * @author nm
 *
 */
public class ParameterWithId {
    final NamedObjectId id; // the id used by the client to subscribe

    final PathElement[] path; // the path to reach the end element in case the subscribed parameter is an aggregate
                              // or array
    final Parameter p; // the parameter the id refers to

    public ParameterWithId(Parameter p, NamedObjectId id, PathElement[] path) {
        this.p = p;
        this.id = id;
        this.path = path;
    }

    public NamedObjectId getId() {
        return id;
    }

    public PathElement[] getPath() {
        return path;
    }

    public Parameter getParameter() {
        return p;
    }

    /**
     * 
     * @return the qualified name of the parameter plus the aggregate part if any
     */
    public String getQualifiedName() {
        if (path == null) {
            return p.getQualifiedName();
        } else {
            return p.getQualifiedName() + AggregateUtil.toString(path);
        }
    }

}
