package org.yamcs.algorithms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.yamcs.events.EventProducer;
import org.yamcs.logging.Log;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.mdb.ProcessorData;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.protobuf.AlgorithmStatus;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Algorithm.Scope;

/**
 * A context is a collection of active algorithms. Each algorithm has only one instance active in a given context.
 * <p>
 * There is normally a global context in a processor and a few contexts related to the command verifiers.
 * <p>
 * The {@link #process(long, ProcessingData)} method will trigger calling all the active algorithms from this context in
 * order.
 *
 */
public class AlgorithmExecutionContext {
    static final Log log = new Log(AlgorithmExecutionContext.class);

    CopyOnWriteArrayList<ActiveAlgorithm> executionOrder = new CopyOnWriteArrayList<>();

    // algorithm tracers fqn -> AlgorithmTrace
    final Map<String, AlgorithmTrace> tracers = new HashMap<>();

    // name used for debugging
    final String contextName;

    final ProcessorData procData;

    final int maxErrCount;

    // stores algorithms deactivated because of too many runtime errors
    private Map<String, AlgorithmStatus> algorithmsInError = new HashMap<>();

    public AlgorithmExecutionContext(String contextName, ProcessorData procData,
            int maxErrCount) {
        this.contextName = contextName;
        this.procData = procData;
        this.maxErrCount = maxErrCount;
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
            AlgorithmStatus.Builder status = activeAlgo.getStatus()
                    .setTraceEnabled(tracers.containsKey(algo.getQualifiedName()))
                    .setActive(false);

            status.setErrorMessage("Deactivated after " + maxErrCount + " errors. Last error: "
                    + status.getErrorMessage());
            algorithmsInError.put(algo.getQualifiedName(), status.build());

            executionOrder.remove(activeAlgo);
        }
        return params;
    }

    public String getName() {
        return contextName;
    }

    public boolean containsAlgorithm(String algoFqn) {
        return executionOrder.stream().anyMatch(aa -> aa.getAlgorithm().getQualifiedName().equals(algoFqn));
    }

    public void addAlgorithm(ActiveAlgorithm activeAlgorithm) {
        executionOrder.add(activeAlgorithm);
    }

    /**
     * remove the active algorithm with the given identifier.
     * <p>
     * The algorithm will not be called in subsequent calls to {@link #process(long, ProcessingData)}
     * 
     * @param algoFqn
     * @return the active algorithm removed or null if there was no active algorithm
     */
    public ActiveAlgorithm removeAlgorithm(String algoFqn) {

        Optional<ActiveAlgorithm> algo = getByFqn(algoFqn);
        if (algo.isPresent()) {
            executionOrder.remove(algo.get());
            algo.get().executor.dispose();
            return algo.get();
        } else {
            return null;
        }
    }

    public ActiveAlgorithm removeAlgorithm(Algorithm algorithm) {
        return removeAlgorithm(algorithm.getQualifiedName());
    }

    public List<ActiveAlgorithm> getActiveAlgorithms() {
        return executionOrder;
    }

    public ProcessorData getProcessorData() {
        return procData;
    }

    public Mdb getMdb() {
        return procData.getMdb();
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
        Optional<ActiveAlgorithm> activeAlgo = getByFqn(fqn);

        if (activeAlgo.isPresent()) {
            activeAlgo.get().addExecListener(trace);
        }
    }

    public synchronized void disableTracing(Algorithm algo) {
        String fqn = algo.getQualifiedName();
        AlgorithmTrace trace = tracers.remove(fqn);

        if (trace != null) {
            Optional<ActiveAlgorithm> activeAlgo = getByFqn(fqn);

            if (activeAlgo.isPresent()) {
                activeAlgo.get().removeExecListener(trace);
            }
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
        return getByFqn(algoFqn).orElse(null);
    }

    public AlgorithmStatus getAlgorithmStatus(String algoFqn) {
        Optional<ActiveAlgorithm> activeAlgo = getByFqn(algoFqn);

        if (activeAlgo.isPresent()) {
            return activeAlgo.get().getStatus().setTraceEnabled(tracers.containsKey(algoFqn)).build();
        } else {
            return algorithmsInError.getOrDefault(algoFqn, AlgorithmStatus.newBuilder().setActive(false).build());
        }
    }

    private Optional<ActiveAlgorithm> getByFqn(String algoFqn) {
        return executionOrder.stream()
                .filter(aa -> aa.getAlgorithm().getQualifiedName().equals(algoFqn)).findAny();
    }

}
