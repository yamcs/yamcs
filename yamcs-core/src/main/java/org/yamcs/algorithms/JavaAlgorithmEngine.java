package org.yamcs.algorithms;

import org.yamcs.YConfiguration;

public class JavaAlgorithmEngine implements AlgorithmEngine {
    JavaAlgorithmExecutorFactory factory = new JavaAlgorithmExecutorFactory();
    
    @Override
    public AlgorithmExecutorFactory makeExecutorFactory(AlgorithmManager algorithmManager,
            AlgorithmExecutionContext context, String language, YConfiguration config) {
        return factory;
    }
}
