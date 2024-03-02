package org.yamcs.xtce.util;

import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.NameDescription;


public class AlgorithmReference extends NameReference {
    @FunctionalInterface
    public interface AlgorithmResolvedAction extends ResolvedAction {
        /**
         * pushes the NameDescription through and returns true if the name reference is resolved and false otherwise
         * 
         * false can be returned in case the NameDescription refers to something which is not itself fully resolved
         */
        public void resolved(Algorithm algo);

        default void resolved(NameDescription nd) {
            resolved((Algorithm) nd);
        }

    }

    public AlgorithmReference(String ref) {
        super(ref, Type.ALGORITHM);
    }

    public void resolved(Algorithm algorithm) {
        result = algorithm;

        for (ResolvedAction ra : actions) {
            if (ra instanceof AlgorithmResolvedAction) {
                ((AlgorithmResolvedAction) ra).resolved(algorithm);
            } else {
                ra.resolved(algorithm);
            }
        }
    }

    public AlgorithmReference addResolvedAction(AlgorithmResolvedAction action) {
        if (result == null) {
            actions.add(action);
        } else {
            action.resolved((Algorithm) result);
        }

        return this;
    }
}
