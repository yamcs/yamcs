package org.yamcs.algorithms;

import java.util.Map;

public class JavaAlgorithmEngine implements AlgorithmEngine {
    JavaAlgorithmExecutorFactory factory = new JavaAlgorithmExecutorFactory();
    
    @Override
    public AlgorithmExecutorFactory makeExecutorFactory(AlgorithmManager algorithmManager, String language,
            Map<String, Object> config) {
        return factory;
    }
}
