package org.yamcs.xtce.util;

import java.util.Iterator;

import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.PathElement;

/**
 * unresolved reference to parameters.
 * 
 * in addition to {@link UnresolvedNameReference} this class can resolve to aggregate members
 * 
 * @author nm
 *
 */
public class UnresolvedParameterReference extends UnresolvedNameReference implements ParameterReference {
    public UnresolvedParameterReference(String ref) {
        super(ref, Type.PARAMETER);
    }

    public boolean tryResolve(Parameter param, PathElement[] path) {
        Iterator<ResolvedAction> it = actions.iterator();
        while (it.hasNext()) {
            ResolvedAction ra = it.next();
            boolean b = false;
            if (ra instanceof ParameterResolvedAction) {
                b = ((ParameterResolvedAction) ra).resolved(param, path);
            } else {
                b = ra.resolved(param);
            }
            if (b) {
                it.remove();
            }
        }
        if (actions.isEmpty()) {
            cf.complete(param);
            return true;
        } else {
            return false;
        }
    }

    public UnresolvedParameterReference addResolvedAction(ParameterResolvedAction action) {
        actions.add(action);
        return this;
    }

}
