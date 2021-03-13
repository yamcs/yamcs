package org.yamcs.algorithms;

import org.yamcs.Processor;
import org.yamcs.YConfiguration;
import org.yamcs.xtce.Algorithm;

/**
 * Handles algorithms for one language.
 * <ul>
 * <li>there is one AlgorithmEngine per language for the entire yamcs server</li>
 * <li>for each AlgorithmManager (i.e. for each {@link Processor}) a new AlgorithmExecutorFactory is created</li>
 * <li>then for each {@link Algorithm} a new {@link AlgorithmExecutor} is created</li>
 * </ul>
 * 
 * @author nm
 *
 */
public interface AlgorithmEngine {
    /**
     * Create an executor factory to be used for the given algorithm manager
     * 
     * @param algorithmManager
     * @param config
     *            - the configuration that was used for the AlgorithmManager in the processor.yaml
     * @return
     */
    AlgorithmExecutorFactory makeExecutorFactory(AlgorithmManager algorithmManager, AlgorithmExecutionContext context,
            String language, YConfiguration config);
}
