package org.yamcs.algorithms;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.commanding.ArgumentValue;
import org.yamcs.events.EventProducer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.parameter.RawEngValue;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Argument;

/**
 * This class stores some info related to one active algorithm
 * 
 * @author nm
 *
 */
public class ActiveAlgorithm {
    static final Logger log = LoggerFactory.getLogger(ActiveAlgorithm.class);
    EventProducer eventProducer;

    /**
     * The MDB definition of the algorithm
     */
    final Algorithm algorithm;

    /**
     * The algorithm executor - responsible for collecting inputs and running the algorithm
     */
    final AlgorithmExecutor executor;

    /**
     * The context in which the algorithm runs
     */
    final AlgorithmExecutionContext context;

    /**
     * how many time the algorithm ran (successful or with error)
     */
    int runCount;
    /**
     * when the algorithm ran the last time
     * <p>
     * This is system (wall clock) time as returned by currentTimeMillis
     */
    long lastRun;

    /**
     * How long the algorithm ran in total nanoseconds
     * <p>
     * totalExecTimeNs/runCount can be used to determine how fast the algorithm is
     */
    long totalExecTimeNs;

    /**
     * How many times the algorithm run with error
     */
    int errorCount;

    /**
     * If the algorithm ever produced an error, this is th error message
     */
    String errorMessage;
    /**
     * When the error has been produced
     * <p>
     * system (wall clock) time
     */
    long errorTime;

    /**
     * Algorithm execution listeners
     */
    final protected CopyOnWriteArrayList<AlgorithmExecListener> execListeners = new CopyOnWriteArrayList<>();

    public ActiveAlgorithm(Algorithm algorithm, AlgorithmExecutionContext context, AlgorithmExecutor executor) {
        this.algorithm = algorithm;
        this.context = context;
        this.executor = executor;
    }

    void algoRun(long lastRun, long duration) {
        runCount++;
        this.lastRun = lastRun;
        totalExecTimeNs += duration;
    }

    void setError(long errorTime, String errorMessage) {
        this.errorTime = errorTime;
        this.errorMessage = errorMessage;
        errorCount++;
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    public AlgorithmExecutionContext getExecutionContext() {
        return context;
    }

    public boolean updateParameters(ParameterValueList paramList) {
        return executor.updateParameters(paramList);
    }

    public boolean updateArguments(Map<Argument, ArgumentValue> args) {
        return executor.updateArguments(args);
    }

    public List<ParameterValue> runAlgorithm(long acqTime, long genTime) {
        runCount++;
        List<ParameterValue> output;
        lastRun = System.currentTimeMillis();

        long t0 = System.nanoTime();
        try {
            AlgorithmExecutionResult result = executor.execute(acqTime, genTime);
            propagateResultToListeners(result);
            output = result.outputValues;
        } catch (Exception e) {
            output = Collections.emptyList();
            errorCount++;
            errorMessage = e.getMessage();
            if (e instanceof AlgorithmException) {
                propagateErrorToListeners(((AlgorithmException) e).inputValues, e.getMessage());
                log.warn("{}", e.getMessage());
            } else {
                log.error("{}", e.getMessage());
                propagateErrorToListeners(null, e.getMessage());
            }

            if (eventProducer != null) {
                eventProducer.sendWarning(e.getMessage());
            }
        }
        long t1 = System.nanoTime();
        totalExecTimeNs += (t1 - t0);

        return output;
    }

    private void propagateResultToListeners(AlgorithmExecutionResult result) {
        try {
            execListeners.forEach(l -> l.algorithmRun(result.inputValues, result.returnValue, result.outputValues));
        } catch (Exception e) {
            log.error("Error invoking algorithm listener", e);
        }
    }

    protected void propagateErrorToListeners(List<RawEngValue> inputValues, String errorMsg) {
        try {
            execListeners.forEach(l -> l.algorithmError(inputValues, errorMsg));
        } catch (Exception e) {
            log.error("Error invoking algorithm listener", e);
        }
    }

    public void addExecListener(AlgorithmExecListener listener) {
        execListeners.add(listener);
    }

    public void removeExecListener(AlgorithmExecListener listener) {
        execListeners.remove(listener);
    }

    @Override
    public String toString() {
        return "AlgorithmStatus [runCount=" + runCount + ", lastRun=" + lastRun + ", errorMessage=" + errorMessage
                + ", errorCount=" + errorCount + ", errorTime=" + errorTime + "]";
    }

}
