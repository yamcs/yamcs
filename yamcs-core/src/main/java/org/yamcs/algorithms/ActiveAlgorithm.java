package org.yamcs.algorithms;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.events.EventProducer;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.RawEngValue;
import org.yamcs.protobuf.AlgorithmStatus;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Algorithm.Scope;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.TriggerSetType;

import com.google.protobuf.util.Timestamps;

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
     * If the algorithm ever produced an error, this is the error message
     */
    private String errorMessage;
    /**
     * When the error has been produced
     * <p>
     * system (wall clock) time
     */
    private long errorTime;

    /**
     * Algorithm execution listeners
     */
    protected final CopyOnWriteArrayList<AlgorithmExecListener> execListeners = new CopyOnWriteArrayList<>();

    public ActiveAlgorithm(Algorithm algorithm, AlgorithmExecutionContext context, AlgorithmExecutor executor) {
        this.algorithm = algorithm;
        this.context = context;
        this.executor = executor;
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

    public boolean update(ProcessingData data) {
        return executor.update(data);
    }

    public List<ParameterValue> runAlgorithm(long acqTime, long genTime, ProcessingData data) {
        runCount++;
        List<ParameterValue> output;
        lastRun = System.currentTimeMillis();

        long t0 = System.nanoTime();
        try {
            AlgorithmExecutionResult result = executor.execute(acqTime, genTime, data);
            propagateResultToListeners(result);
            output = result.getOutputValues();
        } catch (Exception e) {
            output = Collections.emptyList();
            setError(System.currentTimeMillis(), e.getMessage());
            if (e instanceof AlgorithmException) {
                propagateErrorToListeners(((AlgorithmException) e).inputValues, e.getMessage());
                log.warn("Error executing algorithm: {}", e.getMessage());
            } else {
                log.error("Error executing algorithm", e);
                propagateErrorToListeners(null, e.toString());
            }

            if (eventProducer != null) {
                eventProducer.sendWarning(e.toString());
            }
        }
        long t1 = System.nanoTime();
        totalExecTimeNs += (t1 - t0);

        return output;
    }

    private void propagateResultToListeners(AlgorithmExecutionResult result) {
        try {
            execListeners.forEach(
                    l -> l.algorithmRun(result.getInputValues(), result.getReturnValue(), result.getOutputValues()));
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


    /**
     * 
     * gets the last error message produced by the algorithm or null if it never produced an error message
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * gets the system time (wall clock time) when the last error message was produced
     */
    public long getErrorTime() {
        return errorTime;
    }

    /**
     * Gets the number of errors produced by the algorithm
     */
    public long getErrorCount() {
        return errorCount;
    }

    public Scope getScope() {
        return algorithm.getScope();
    }

    public AlgorithmStatus.Builder getStatus() {
        AlgorithmStatus.Builder statusb = AlgorithmStatus.newBuilder()
                .setActive(true)
                .setRunCount(runCount)
                .setErrorCount(errorCount);
        if (errorMessage != null) {
            statusb.setErrorMessage(errorMessage);
            statusb.setErrorTime(Timestamps.fromMillis(errorTime));
        }
        statusb.setLastRun(Timestamps.fromMillis(lastRun));
        statusb.setExecTimeNs(totalExecTimeNs);

        return statusb;
    }

    /**
     *
     * @see {@link AlgorithmExecutor#getInputList()}
     */
    public List<InputParameter> getInputList() {
        return executor.getInputList();
    }

    /**
     *
     * @see {@link AlgorithmExecutor#getOutputList()}
     */
    public List<OutputParameter> getOutputList() {
        return executor.getOutputList();
    }

    public TriggerSetType getTriggerSet() {
        return algorithm.getTriggerSet();
    }

    public String getName() {
        return algorithm.getName();
    }

    @Override
    public String toString() {
        return "ActiveAlgorithm " + algorithm.getName() + "[runCount=" + runCount + ", lastRun=" + lastRun
                + ", errorMessage=" + errorMessage + ", errorCount=" + errorCount + ", errorTime=" + errorTime + "]";
    }
}
