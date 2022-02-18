package org.yamcs.algorithms;

import java.util.List;

import org.yamcs.xtce.CustomAlgorithm;

/**
 * Responsible for creating algorithm executors.
 * <p>
 * One such factory exists for every supported language.
 * 
 * @author nm
 *
 */
public interface AlgorithmExecutorFactory {
    /**
     * Creates a new executor for the algorithm running in the execution context
     * 
     * @param alg
     *            - the algorithm definition
     * @param execCtx
     *            - the algorithm execution context
     * @return
     * @throws AlgorithmException
     */
    AlgorithmExecutor makeExecutor(CustomAlgorithm alg, AlgorithmExecutionContext execCtx) throws AlgorithmException;

    /**
     * Returns all the languages supported by this factory.
     * Used in order to not create new factories for the same language with different names (e.g. JavaScript and
     * ECMAScript)
     * 
     * @return
     */
    List<String> getLanguages();
}
