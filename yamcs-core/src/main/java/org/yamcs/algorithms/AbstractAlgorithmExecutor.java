package org.yamcs.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.OnParameterUpdateTrigger;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;

/**
 * Skeleton implementation for algorithms conforming to the XTCE {@link Algorithm} definition.
 * 
 * - it collects all the inputs into an inputList and implements the triggering based on the mandatory parameters.
 * 
 */
public abstract class AbstractAlgorithmExecutor implements AlgorithmExecutor {
    final protected AlgorithmExecutionContext execCtx;
    final protected Algorithm algorithmDef;

    final protected CopyOnWriteArrayList<AlgorithmExecListener> execListeners = new CopyOnWriteArrayList<>();
    static protected final Logger log = LoggerFactory.getLogger(AbstractAlgorithmExecutor.class);

    // Collect all the input values here - the indexes match one to one the algorithm def input list
    final protected List<ParameterValue> inputValues;

    public AbstractAlgorithmExecutor(Algorithm algorithmDef, AlgorithmExecutionContext execCtx) {
        this.algorithmDef = algorithmDef;
        this.execCtx = execCtx;
        List<InputParameter> l = algorithmDef.getInputList();
        inputValues = new ArrayList<>(l.size());
        for (int k = 0; k < l.size(); k++) {
            inputValues.add(null);
        }
    }

    /**
     * update the parameters and return true if the algorithm should run
     * 
     * @param items
     * @return true if the algorithm should run
     */
    public synchronized boolean updateParameters(List<ParameterValue> items) {
        boolean skipRun = false;
        List<InputParameter> l = algorithmDef.getInputList();

        for (int k = 0; k < l.size(); k++) {
            InputParameter inputParameter = l.get(k);
            ParameterInstanceRef pInstance = inputParameter.getParameterInstance();
            for (ParameterValue pval : items) {
                if(pval.getAcquisitionStatus() == AcquisitionStatus.INVALID) {
                    continue;
                }
                if (pInstance.getParameter().equals(pval.getParameter())) {
                    if (getLookbackSize(pInstance.getParameter()) == 0) {
                        updateInput(k, inputParameter, pval);
                        inputValues.set(k, pval);
                    } else {
                        ParameterValue historicValue = execCtx.getHistoricValue(pInstance);
                        if (historicValue != null) {
                            updateInput(k, inputParameter, historicValue);
                            inputValues.set(k, historicValue);
                        }
                    }
                }
            }
            if (!skipRun && inputParameter.isMandatory() && inputValues.get(k) == null) {
                log.trace("Not running algorithm {} because mandatory input {} is not present", algorithmDef.getName(),
                        inputParameter.getInputName());
                skipRun = true;
            }
        }

        // But run it only, if this satisfies an onParameterUpdate trigger
        boolean triggered = false;
        for (OnParameterUpdateTrigger trigger : algorithmDef.getTriggerSet().getOnParameterUpdateTriggers()) {
            if (triggered) {
                break;
            }
            for (ParameterValue pval : items) {
                if (pval.getParameter().equals(trigger.getParameter())) {
                    triggered = true;
                    break;
                }
            }
        }
        boolean shouldRun = (!skipRun && triggered);
        return shouldRun;
    }

    /**
     * Called when the given inputParameter receives a value.
     * idx is the index of the inputParameter in the algorithm definition input list
     * Note that all values are also collected in the inputList
     * 
     * @param inputParameter
     * @param newValue
     */
    protected void updateInput(int idx, InputParameter inputParameter, ParameterValue newValue) {
    }

    protected void propagateToListeners(Object returnValue, List<ParameterValue> outputValues) {
        for (AlgorithmExecListener listener : execListeners) {
            listener.algorithmRun(returnValue, outputValues);
        }
    }

    @Override
    public void addExecListener(AlgorithmExecListener listener) {
        execListeners.add(listener);
    }

    @Override
    public AlgorithmExecutionContext getExecutionContext() {
        return execCtx;
    }

    @Override
    public int getLookbackSize(Parameter parameter) {
        // e.g. [ -3, -2, -1, 0 ]
        int min = 0;
        for (InputParameter p : algorithmDef.getInputSet()) {
            ParameterInstanceRef pInstance = p.getParameterInstance();
            if (pInstance.getParameter().equals(parameter) && pInstance.getInstance() < min) {
                min = p.getParameterInstance().getInstance();
            }
        }
        return -min;
    }

    @Override
    public Set<Parameter> getRequiredParameters() {
        return algorithmDef.getInputList().stream()
                .map(ip -> ip.getParameterInstance().getParameter())
                .collect(Collectors.toSet());
    }

    @Override
    public Algorithm getAlgorithm() {
        return algorithmDef;
    }
}
