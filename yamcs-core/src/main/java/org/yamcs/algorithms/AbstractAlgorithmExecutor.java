package org.yamcs.algorithms;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.OnParameterUpdateTrigger;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;

public abstract class AbstractAlgorithmExecutor implements AlgorithmExecutor {
    final protected AlgorithmExecutionContext execCtx;
    final protected Algorithm algorithmDef;
    final protected CopyOnWriteArrayList<AlgorithmExecListener> execListeners = new CopyOnWriteArrayList<AlgorithmExecListener>();
    // Keep only unique arguments (for subscription purposes)
    protected Set<Parameter> requiredParameters = new HashSet<Parameter>();
    protected Set<InputParameter>mandatoryToRun = new HashSet<InputParameter>();
    private Map<InputParameter,ParameterValue> inputValues = new HashMap<InputParameter,ParameterValue>();
    
    
    static final Logger log = LoggerFactory.getLogger(AbstractAlgorithmExecutor.class);
    
    public AbstractAlgorithmExecutor(Algorithm algorithmDef, AlgorithmExecutionContext execCtx) {
        this.algorithmDef = algorithmDef;
        this.execCtx = execCtx;
        for(InputParameter inputParameter:algorithmDef.getInputSet()) {
            requiredParameters.add(inputParameter.getParameterInstance().getParameter());

            // Default-define all input values to null to prevent ugly runtime errors
            String scriptName = inputParameter.getInputName();
            if(scriptName==null) {
                scriptName = inputParameter.getParameterInstance().getParameter().getName();
            }
           
            if(inputParameter.isMandatory()) mandatoryToRun.add(inputParameter);
        }
    }
    
    /**
     * update the parameters and return true if the algorithm should run
     * @param items
     * @return true if the algorithm should run
     */
    public synchronized boolean updateParameters(ArrayList<ParameterValue> items) {
        ArrayList<ParameterValue> allItems=new ArrayList<ParameterValue>(items);
        boolean skipRun=false;

        // Set algorithm arguments based on incoming values
        for(InputParameter inputParameter:algorithmDef.getInputSet()) {
            ParameterInstanceRef pInstance = inputParameter.getParameterInstance();
            for(ParameterValue pval:allItems) {
                if(pInstance.getParameter().equals(pval.getParameter())) {
                    if(getLookbackSize(pInstance.getParameter())==0) {
                        updateInput(inputParameter, pval);
                        inputValues.put(inputParameter, pval);
                    } else {
                        ParameterValue historicValue=execCtx.getHistoricValue(pInstance);
                        if(historicValue!=null) {
                            updateInput(inputParameter, historicValue);
                            inputValues.put(inputParameter, historicValue);
                        }
                    }
                }
            }
            if(!skipRun && inputParameter.isMandatory() && !inputValues.containsKey(inputParameter)) {
                log.trace("Not running algorithm {} because mandatory input {} is not present", algorithmDef.getName(), inputParameter.getInputName());
                skipRun = true;
            }
        }

        // But run it only, if this satisfies an onParameterUpdate trigger
        boolean triggered=false;
        for(OnParameterUpdateTrigger trigger:algorithmDef.getTriggerSet().getOnParameterUpdateTriggers()) {
            if(triggered) break;
            for(ParameterValue pval:allItems) {
                if(pval.getParameter().equals(trigger.getParameter())) {
                    triggered=true;
                    break;
                }
            }
        }
        boolean shouldRun =(!skipRun && triggered);
        return shouldRun;
    }
   

    abstract protected void updateInput(InputParameter inputParameter, ParameterValue newValue);
    
    protected void propagateToListeners(Object returnValue,  List<ParameterValue> outputValues){
        for(AlgorithmExecListener listener: execListeners) {
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
        int min=0;
        for(InputParameter p:algorithmDef.getInputSet()) {
            ParameterInstanceRef pInstance=p.getParameterInstance();
            if(pInstance.getParameter().equals(parameter) && pInstance.getInstance()<min) {
                min=p.getParameterInstance().getInstance();
            }
        }
        return -min;
    }
    @Override
    public Set<Parameter> getRequiredParameters() {
        return requiredParameters;
    }
    @Override
    public Algorithm getAlgorithm() {
        return algorithmDef;
    }
}
