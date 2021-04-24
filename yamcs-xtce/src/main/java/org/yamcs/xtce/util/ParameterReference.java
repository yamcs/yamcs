package org.yamcs.xtce.util;

import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.PathElement;

public class ParameterReference extends NameReference {
    @FunctionalInterface
    public interface ParameterResolvedAction extends ResolvedAction {
        /**
         * pushes the NameDescription through and returns true if the name reference is resolved and false otherwise
         * 
         * false can be returned in case the NameDescription refers to something which is not itself fully resolved
         * 
         * if path is not null, it means that the reference has been resolved to a path inside an aggregate parameter
         */
        public void resolved(Parameter parameter, PathElement[] path);

        default void resolved(NameDescription nd) {
            resolved((Parameter) nd, null);
        }

    }

    PathElement[] resultPath;

    public ParameterReference(String ref) {
        super(ref, Type.PARAMETER);
    }

    public void resolved(Parameter param, PathElement[] path) {
        result = param;
        resultPath = path;

        for (ResolvedAction ra : actions) {
            if (ra instanceof ParameterResolvedAction) {
                ((ParameterResolvedAction) ra).resolved(param, path);
            } else {
                ra.resolved(param);
            }
        }
    }

    public ParameterReference addResolvedAction(ParameterResolvedAction action) {
        if (result == null) {
            actions.add(action);
        } else {
            action.resolved((Parameter) result, resultPath);
        }

        return this;
    }

}
