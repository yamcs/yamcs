package org.yamcs.algorithms;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.yamcs.events.EventProducer;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.protobuf.AlgorithmStatus;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Algorithm.Scope;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.ProcessingData;
import org.yamcs.xtceproc.ProcessorData;

/**
 * Algorithms for command verifiers must execute in parallel in different contexts - meaning that each algorithm will
 * have their own values for inputs referring to command specifics (e.g. command sequence count)
 * <p>
 * That's why we associate to each AlgorithmExecutor (which represents the instantiation of one algorithm) one of these
 * AlgorithmExecutionContext.
 * <p>
 * Currently it stores the historical values for parameters requiring that.
 * <p>
 * Each execution context has a parent that stores the values which are not context specific.
 * 
 *
 */
public class AlgorithmExecutionContext {
    // For storing a window of previous parameter instances
    final AlgorithmExecutionContext parent;
    final static Log log = new Log(AlgorithmExecutionContext.class);

    // all the algorithms that run in this context
    // fqn -> ActiveAlgorithm
    final HashMap<String, ActiveAlgorithm> activeAlgorithms = new HashMap<>();

    CopyOnWriteArrayList<ActiveAlgorithm> executionOrder = new CopyOnWriteArrayList<>();

    // algorithm tracers fqn -> AlgorithmTrace
    final Map<String, AlgorithmTrace> tracers = new HashMap<>();

    // name used for debugging
    final String contextName;

    final ProcessorData procData;

    final int maxErrCount;

    // stores algorithms deactivated because of too many runtime errors
    Map<String, AlgorithmStatus> algorithmsInError = new HashMap<>();

    public AlgorithmExecutionContext(String contextName, AlgorithmExecutionContext parent, ProcessorData procData,
            int maxErrCount) {
        this.contextName = contextName;
        this.parent = parent;
        this.procData = procData;
        this.maxErrCount = maxErrCount;
    }

    public void deactivateAlgorithm(Algorithm algorithm) {
        ActiveAlgorithm activeAlgo = activeAlgorithms.remove(algorithm.getQualifiedName());
        if (activeAlgo != null) {
            executionOrder.remove(activeAlgo);
        }
    }

    /**
     * Update the input data and run the affected algorithms
     * <p>
     * Add the result of the algorithms to the processing data
     * 
     */
    public void process(long acqTime, ProcessingData data) {
        ParameterValueList tmParams = data.getTmParams();
        ParameterValueList cmdParams = data.getCmdParams();
        long genTime = acqTime;
        if (tmParams != null && !tmParams.isEmpty()) {
            genTime = tmParams.getFirst().getGenerationTime();
        } else if (cmdParams != null && !cmdParams.isEmpty()) {
            genTime = cmdParams.getFirst().getGenerationTime();
        }

        for (ActiveAlgorithm activeAlgo : executionOrder) {
            boolean shouldRun = activeAlgo.update(data);
            if (shouldRun) {
                log.trace("Running algorithm {}", activeAlgo.getAlgorithm().getName());
                List<ParameterValue> r = runAlgorithm(activeAlgo, acqTime, genTime, data);
                if (r == null || r.isEmpty()) {
                    continue;
                }
                if (activeAlgo.getScope() == Scope.GLOBAL) {
                    if (tmParams != null) {
                        tmParams.addAll(r);
                    }
                } else if (cmdParams != null) {
                    for (ParameterValue pv : r) {
                        if (pv.getParameter().isCommandParameter()) {
                            cmdParams.add(pv);
                        } else if (tmParams != null) {
                            tmParams.add(pv);
                        }
                    }
                }
            }
        }
    }

    List<ParameterValue> runAlgorithm(ActiveAlgorithm activeAlgo, long acqTime, long genTime, ProcessingData data) {
        List<ParameterValue> params = activeAlgo.runAlgorithm(acqTime, genTime, data);
        if (activeAlgo.getErrorCount() >= maxErrCount) {
            Algorithm algo = activeAlgo.getAlgorithm();
            log.warn("Algorithm {} has faulted {} times, deactivating", algo.getQualifiedName(),
                    activeAlgo.getErrorCount());
            algorithmsInError.put(algo.getQualifiedName(),
                    activeAlgo.getStatus(tracers.containsKey(algo.getQualifiedName())));
            executionOrder.remove(activeAlgo);
        }
        return params;
    }

    public String getName() {
        return contextName;
    }

    public boolean containsAlgorithm(String algoFqn) {
        return activeAlgorithms.containsKey(algoFqn);
    }

    public void addAlgorithm(ActiveAlgorithm activeAlgorithm) {
        activeAlgorithms.put(activeAlgorithm.getAlgorithm().getQualifiedName(), activeAlgorithm);
        executionOrder.add(activeAlgorithm);
    }

    public ActiveAlgorithm removeAlgorithm(String algoFqn) {
        ActiveAlgorithm algo = activeAlgorithms.remove(algoFqn);
        if (algo != null) {
            executionOrder.remove(algo);
        }
        return algo;
    }

    public Collection<Algorithm> getAlgorithms() {
        return activeAlgorithms.values().stream().map(aa -> aa.getAlgorithm()).collect(Collectors.toList());
    }

    public ActiveAlgorithm remove(String algoFqn) {
        return activeAlgorithms.remove(algoFqn);
    }

    public ProcessorData getProcessorData() {
        return procData;
    }

    public XtceDb getXtceDb() {
        return procData.getXtceDb();
    }

    public EventProducer getEventProducer() {
        return procData.getEventProducer();
    }

    public synchronized void enableTracing(Algorithm algo) {
        String fqn = algo.getQualifiedName();
        if (tracers.containsKey(fqn)) {
            return;
        }
        AlgorithmTrace trace = new AlgorithmTrace();
        tracers.put(fqn, trace);
        ActiveAlgorithm activeAlgo = activeAlgorithms.get(fqn);
        if (activeAlgo != null) {
            activeAlgo.addExecListener(trace);
        }
    }

    public synchronized void disableTracing(Algorithm algo) {
        String fqn = algo.getQualifiedName();
        AlgorithmTrace trace = tracers.remove(fqn);

        if (trace != null) {
            ActiveAlgorithm activeAlgo = activeAlgorithms.get(fqn);
            activeAlgo.removeExecListener(trace);
        }
    }

    public synchronized AlgorithmTrace getTrace(String algoFqn) {
        return tracers.get(algoFqn);
    }

    public void logTrace(String algoFqn, String msg) {
        AlgorithmTrace trace = tracers.get(algoFqn);
        if (trace != null) {
            trace.addLog(msg);
        }
    }

    public ActiveAlgorithm getAlgorithm(String algoFqn) {
        return activeAlgorithms.get(algoFqn);
    }

    public AlgorithmStatus getAlgorithmStatus(String algoFqn) {
        ActiveAlgorithm activeAlgo = activeAlgorithms.get(algoFqn);
        if (activeAlgo == null) {
            return AlgorithmStatus.newBuilder().setActive(false).build();
        }

        return activeAlgo.getStatus(tracers.containsKey(algoFqn));
    }

}
