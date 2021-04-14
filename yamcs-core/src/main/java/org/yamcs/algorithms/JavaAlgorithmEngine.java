package org.yamcs.algorithms;

import org.yamcs.YConfiguration;

public class JavaAlgorithmEngine implements AlgorithmEngine {
    JavaAlgorithmExecutorFactory javaFactory = new JavaAlgorithmExecutorFactory();
    JavaExprAlgorithmExecutionFactory javaExprFactory = new JavaExprAlgorithmExecutionFactory();

    @Override
    public AlgorithmExecutorFactory makeExecutorFactory(AlgorithmManager algorithmManager,
            AlgorithmExecutionContext context, String language, YConfiguration config) {
        if ("java".equalsIgnoreCase(language)) {
            return javaFactory;
        } else if ("java-expression".equalsIgnoreCase(language)) {
            return javaExprFactory;
        } else {
            throw new IllegalArgumentException("Unknown lanaguage '" + language + "'");
        }
    }
}
