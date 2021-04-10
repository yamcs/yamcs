package org.yamcs.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.commanding.ArgumentValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.parameter.RawEngValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentInstanceRef;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.OnParameterUpdateTrigger;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.TriggerSetType;

/**
 * Skeleton implementation for algorithms conforming to the XTCE {@link Algorithm} definition.
 * <p>
 * It collects all the inputs into an inputList and implements the triggering based on the mandatory parameters.
 * 
 */
public abstract class AbstractAlgorithmExecutor implements AlgorithmExecutor {
    final protected AlgorithmExecutionContext execCtx;
    final protected Algorithm algorithmDef;

    static protected final Logger log = LoggerFactory.getLogger(AbstractAlgorithmExecutor.class);

    // Collect all the input values here - the indexes match one to one the algorithm def input list
    final protected List<RawEngValue> inputValues;

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
     * @param currentDelivery
     * @return true if the algorithm should run
     */
    @Override
    public synchronized boolean updateParameters(ParameterValueList currentDelivery) {
        boolean skipRun = false;
        List<InputParameter> l = algorithmDef.getInputList();

        for (int k = 0; k < l.size(); k++) {
            InputParameter inputParameter = l.get(k);
            ParameterInstanceRef pInstance = inputParameter.getParameterInstance();
            if (pInstance == null) { // this happens when the input references an argument
                continue;
            }

            // FIXME deal with multiple values of the same parameter in the same delivery
            ParameterValue pval = currentDelivery.getFirstInserted(pInstance.getParameter());

            if (pval != null && pval.getAcquisitionStatus() == AcquisitionStatus.ACQUIRED) {
                if (AlgorithmUtils.getLookbackSize(algorithmDef, pInstance.getParameter()) == 0) {
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
            if (!skipRun && inputParameter.isMandatory() && inputValues.get(k) == null) {
                log.trace("Not running algorithm {} because mandatory input {} is not present",
                        algorithmDef.getName(),
                        inputParameter.getInputName());
                skipRun = true;
            }
        }

        // But run it only, if this satisfies an onParameterUpdate trigger
        boolean triggered = false;
        TriggerSetType triggerSet = algorithmDef.getTriggerSet();
        if (triggerSet == null) {
            triggered = true;
        } else {
            for (OnParameterUpdateTrigger trigger : triggerSet.getOnParameterUpdateTriggers()) {
                ParameterValue pval = currentDelivery.getFirstInserted(trigger.getParameter());
                if (pval != null) {
                    triggered = true;
                    break;
                }
            }
            if (!skipRun && !triggered && log.isTraceEnabled()) {
                log.trace("Not running algorithm {} because the parameter update triggers are not satisified: {}",
                        algorithmDef.getName(),
                        algorithmDef.getTriggerSet().getOnParameterUpdateTriggers());
            }
        }
        boolean shouldRun = (!skipRun && triggered);
        return shouldRun;
    }

    public synchronized boolean updateArguments(Map<Argument, ArgumentValue> args) {
        boolean skipRun = false;
        List<InputParameter> l = algorithmDef.getInputList();

        for (int k = 0; k < l.size(); k++) {
            InputParameter inputParameter = l.get(k);
            ArgumentInstanceRef argRef = inputParameter.getArgumentRef();
            if (argRef == null) {
                if (!skipRun && inputParameter.isMandatory() && inputValues.get(k) == null) {
                    log.trace("Not running algorithm {} because mandatory input {} is not present",
                            algorithmDef.getName(),
                            inputParameter.getInputName());
                    skipRun = true;
                }
                continue;
            }

            ArgumentValue aval = args.get(argRef.getArgument());
            if (aval == null) {
                log.error("Cannot find value for argument " + argRef.getArgument());
                continue;
            }
            updateInputArgument(k, inputParameter, aval);
            inputValues.set(k, aval);

        }
        boolean triggered = algorithmDef.getTriggerSet() == null;

        if (!skipRun && !triggered && log.isTraceEnabled()) {
            log.trace("Not running algorithm {} because the parameter update triggers are not satisified: {}",
                    algorithmDef.getName(),
                    algorithmDef.getTriggerSet().getOnParameterUpdateTriggers());
        }
        boolean shouldRun = (!skipRun && triggered);
        return shouldRun;
    }

    /**
     * Called when the given inputParameter receives a value. idx is the index of the inputParameter in the algorithm
     * definition input list.
     * <p>
     * newValue can be either a {@link ParameterValue} or a {@link ArgumentValue}
     * <p>
     * Can be used by subclasses to perform specific actions;
     * <p>
     * Note that all values are also collected in the inputList
     * 
     * @param inputParameter
     * @param newValue
     */
    protected void updateInput(int idx, InputParameter inputParameter, ParameterValue newValue) {
    }

    /**
     * Called when the given inputParameter which contains a reference to an argument receives an argument value.
     * <p>
     * idx is the index of the inputParameter in the algorithm.
     *
     * @param idx
     * @param inputParameter
     * @param newValue
     */
    protected void updateInputArgument(int idx, InputParameter inputParameter, ArgumentValue newValue) {
    }

    /**
     * Returns the output parameter with the given index.
     * 
     * @param idx
     * @return
     */
    protected Parameter getOutputParameter(int idx) {
        return algorithmDef.getOutputSet().get(idx).getParameter();
    }

    @Override
    public AlgorithmExecutionContext getExecutionContext() {
        return execCtx;
    }

    @Override
    public Algorithm getAlgorithm() {
        return algorithmDef;
    }
}
