package org.yamcs.algorithms;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.events.EventProducer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.AlgorithmStatus;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.ProcessorData;

import com.google.protobuf.util.Timestamps;

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
 * @author nm
 *
 */
public class AlgorithmExecutionContext {
    // For storing a window of previous parameter instances
    HashMap<Parameter, WindowBuffer> buffersByParam = new HashMap<>();
    final AlgorithmExecutionContext parent;

    // all the algorithms that run in this context
    final HashMap<Algorithm, ActiveAlgorithm> activeAlgorithms = new HashMap<>();

    // algorithm tracers fqn -> AlgorithmTrace
    final Map<String, AlgorithmTrace> tracers = new HashMap<>();

    // name used for debugging
    final String contextName;

    final ProcessorData procData;

    public AlgorithmExecutionContext(String contextName, AlgorithmExecutionContext parent, ProcessorData procData) {
        this.contextName = contextName;
        this.parent = parent;
        this.procData = procData;
    }

    public boolean enableBuffer(Parameter param, int lookbackSize) {
        boolean enabled = false;
        if (parent == null || param.getDataSource() == DataSource.COMMAND
                || param.getDataSource() == DataSource.COMMAND_HISTORY) {
            if (buffersByParam.containsKey(param)) {
                WindowBuffer buf = buffersByParam.get(param);
                buf.expandIfNecessary(lookbackSize + 1);
            } else {
                buffersByParam.put(param, new WindowBuffer(lookbackSize + 1));
                enabled = true;
            }
        } else {
            enabled = parent.enableBuffer(param, lookbackSize);
        }
        return enabled;
    }

    public void updateHistoryWindows(List<ParameterValue> pvals) {
        for (ParameterValue pval : pvals) {
            if (buffersByParam.containsKey(pval.getParameter())) {
                buffersByParam.get(pval.getParameter()).update(pval);
            } else if (parent != null) {
                parent.updateHistoryWindow(pval);
            }
        }
    }

    public void updateHistoryWindow(ParameterValue pval) {
        if (buffersByParam.containsKey(pval.getParameter())) {
            buffersByParam.get(pval.getParameter()).update(pval);
        }
    }

    public ParameterValue getHistoricValue(ParameterInstanceRef pInstance) {
        WindowBuffer wb = buffersByParam.get(pInstance.getParameter());
        if (wb != null) {
            return wb.getHistoricValue(pInstance.getInstance());
        } else if (parent != null) {
            return parent.getHistoricValue(pInstance);
        } else {
            return null;
        }
    }

    public String getName() {
        return contextName;
    }

    public boolean containsAlgorithm(Algorithm algo) {
        return activeAlgorithms.containsKey(algo);
    }

    public AlgorithmExecutor getExecutor(Algorithm algo) {
        return activeAlgorithms.get(algo).executor;
    }

    public void addAlgorithm(ActiveAlgorithm activeAlgorithm) {
        activeAlgorithms.put(activeAlgorithm.getAlgorithm(), activeAlgorithm);
    }

    public Collection<Algorithm> getAlgorithms() {
        return activeAlgorithms.keySet();
    }

    public ActiveAlgorithm remove(Algorithm algo) {
        return activeAlgorithms.remove(algo);
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
        ActiveAlgorithm activeAlgo = activeAlgorithms.get(algo);
        if (activeAlgo != null) {
            activeAlgo.addExecListener(trace);
        }
    }

    public synchronized void disableTracing(Algorithm algo) {
        String fqn = algo.getQualifiedName();
        AlgorithmTrace trace = tracers.remove(fqn);

        if (trace != null) {
            ActiveAlgorithm activeAlgo = activeAlgorithms.get(algo);
            activeAlgo.removeExecListener(trace);
        }
    }

    public synchronized AlgorithmTrace getTrace(Algorithm algo) {
        return tracers.get(algo.getQualifiedName());
    }

    public void logTrace(String algoFqn, String msg) {
        AlgorithmTrace trace = tracers.get(algoFqn);
        if (trace != null) {
            trace.addLog(msg);
        }
    }

    public ActiveAlgorithm getActiveAlgorithm(Algorithm algo) {
        return activeAlgorithms.get(algo);
    }

    public AlgorithmStatus getAlgorithmStatus(Algorithm algo) {
        ActiveAlgorithm activeAlgo = activeAlgorithms.get(algo);
        if (activeAlgo == null) {
            return AlgorithmStatus.newBuilder().setActive(false).build();
        }

        AlgorithmStatus.Builder statusb = AlgorithmStatus.newBuilder()
                .setActive(true)
                .setRunCount(activeAlgo.runCount)
                .setErrorCount(activeAlgo.errorCount);
        if (activeAlgo.errorMessage != null) {
            statusb.setErrorMessage(activeAlgo.errorMessage);
            statusb.setErrorTime(Timestamps.fromMillis(activeAlgo.errorTime));
        }

        statusb.setExecTimeNs(activeAlgo.totalExecTimeNs);
        return statusb.build();
    }

}
