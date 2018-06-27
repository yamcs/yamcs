package org.yamcs.xtce.util;

import java.util.Iterator;

import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.PathElement;

/**
 * unresolved reference to parameters.
 * 
 * in addition to {@link UnresolvedNameReference} this class can resolve to aggregate members
 * 
 * @author nm
 *
 */
public class UnresolvedParameterReference extends UnresolvedNameReference {
    public UnresolvedParameterReference(String ref) {
        super(ref, Type.PARAMETER);
    }

    @FunctionalInterface
    public interface ParameterResolvedAction extends ResolvedAction {
        /**
         * pushes the NameDescription through and returns true if the name reference is resolved and false otherwise
         * 
         * false can be returned in case the NameDescription refers to something which is not itself fully resolved
         * 
         * if path is not null, it means that the reference has been resolved to a path inside an aggregate parameter
         */
        public boolean resolved(NameDescription nd, PathElement[] path);

        default boolean resolved(NameDescription nd) {
            return resolved(nd, null);
        }

    }

    public boolean resolved(NameDescription nd, PathElement[] path) {
        Iterator<ResolvedAction> it = actions.iterator();
        while (it.hasNext()) {
            ResolvedAction ra = it.next();
            boolean b = false;
            if (ra instanceof ParameterResolvedAction) {
                b = ((ParameterResolvedAction) ra).resolved(nd, path);
            } else {
                b = ra.resolved(nd);
            }
            if (b) {
                it.remove();
            }
        }
        return actions.isEmpty();
    }

    public UnresolvedParameterReference addResolvedAction(ParameterResolvedAction action) {
        actions.add(action);
        return this;
    }

}
