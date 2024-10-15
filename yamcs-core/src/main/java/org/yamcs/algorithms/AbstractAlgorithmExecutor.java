package org.yamcs.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.commanding.ArgumentValue;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.RawEngValue;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Algorithm.Scope;
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
    boolean firstUpdate = true;

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
     * @param processingData
     * @return true if the algorithm should run
     */
    @Override
    public synchronized boolean update(ProcessingData processingData) {

        boolean skipRun = false;
        List<InputParameter> l = algorithmDef.getInputList();

        for (int k = 0; k < l.size(); k++) {
            InputParameter inputParameter = l.get(k);
            ParameterInstanceRef pref = inputParameter.getParameterInstance();
            if (pref == null) {
                ArgumentValue argval = getInputArgument(processingData, inputParameter.getArgumentRef());
                if (argval != null) {
                    updateInputArgument(k, inputParameter, argval);
                    inputValues.set(k, argval);
                }
            } else {
                ParameterValue pval = getInputParameter(processingData, pref);
                if (pval != null) {
                    updateInput(k, inputParameter, pval);
                    inputValues.set(k, pval);
                }
            }

            if (!skipRun && inputParameter.isMandatory() && inputValues.get(k) == null) {
                log.trace("Not running algorithm {} because mandatory input {} is not present",
                        algorithmDef.getName(),
                        inputParameter.getEffectiveInputName());
                skipRun = true;
            }
        }
        firstUpdate = false;
        // But run it only, if this satisfies an onParameterUpdate trigger
        boolean triggered = false;
        TriggerSetType triggerSet = algorithmDef.getTriggerSet();
        if (triggerSet == null || triggerSet.isEmpty()) {
            // In XTCE, verifier algorithms don't have explicit triggers
            if (algorithmDef.getScope() == Scope.COMMAND_VERIFICATION) {
                var parameterInputs = algorithmDef.getInputList().stream()
                        .filter(input -> input.getParameterInstance() != null)
                        .map(input -> input.getParameterInstance().getParameter())
                        .collect(Collectors.toList());
                if (!parameterInputs.isEmpty()) {
                    for (var p : parameterInputs) {
                        if (processingData.containsUpdate(p)) {
                            triggered = true;
                            break;
                        }
                    }
                } else {
                    triggered = true;
                }
            } else {
                triggered = true;
            }
        } else {
            for (OnParameterUpdateTrigger trigger : triggerSet.getOnParameterUpdateTriggers()) {
                if (processingData.containsUpdate(trigger.getParameter())) {
                    triggered = true;
                    break;
                }
            }
            if (!skipRun && !triggered && log.isTraceEnabled()) {
                log.trace("Not running algorithm {} because the parameter update triggers are not satisfied: {}",
                        algorithmDef.getName(),
                        algorithmDef.getTriggerSet().getOnParameterUpdateTriggers());
            }
        }
        boolean shouldRun = (!skipRun && triggered);
        return shouldRun;
    }

    public ParameterValue getInputParameter(ProcessingData processingData, ParameterInstanceRef pref) {
        ParameterValue pval = null;
        pval = processingData.getParameterInstance(pref);
        if (pval == null) {
            return null;
        }

        if (pref.getMemberPath() != null) {
            ParameterValue memberValue = AggregateUtil.extractMember(pval, pref.getMemberPath());
            if (memberValue == null) {
                // this can happen for an array which does not have enough elements
                log.debug("value {} does not have member path required by parameter reference {}",
                        pval, pref);
            }
            pval = memberValue;
        }
        return pval;
    }

    public ArgumentValue getInputArgument(ProcessingData processingData, ArgumentInstanceRef ref) {
        ArgumentValue aval = processingData.getCmdArgument(ref.getArgument());
        if (aval == null) {
            return null;
        }

        if (ref.getMemberPath() != null) {
            ArgumentValue memberValue = AggregateUtil.extractMember(aval, ref.getMemberPath());
            if (memberValue == null) {
                // this can happen for an array which does not have enough elements
                log.debug("value {} does not have member path required by parameter reference {}",
                        aval, ref);
            }
            aval = memberValue;
        }
        return aval;
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
