package org.yamcs.xtce.util;

import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.PathElement;

public interface ParameterReference extends NameReference {
    @FunctionalInterface
    public interface ParameterResolvedAction extends ResolvedAction {
        /**
         * pushes the NameDescription through and returns true if the name reference is resolved and false otherwise
         * 
         * false can be returned in case the NameDescription refers to something which is not itself fully resolved
         * 
         * if path is not null, it means that the reference has been resolved to a path inside an aggregate parameter
         */
        public boolean resolved(Parameter parameter, PathElement[] path);

        default boolean resolved(NameDescription nd) {
            return resolved((Parameter) nd, null);
        }

    }

    public boolean tryResolve(Parameter parameter, PathElement[] path);

    public ParameterReference addResolvedAction(ParameterResolvedAction action);
}
