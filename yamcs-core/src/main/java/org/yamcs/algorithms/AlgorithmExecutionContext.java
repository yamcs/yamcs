package org.yamcs.algorithms;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.yamcs.Processor;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;

/**
 * Algorithms for command verifiers must execute in parallel in different contexts - meaning that each algorithm will have 
 * their own values for inputs referring to command specifics (e.g. command sequence count) 
 * 
 * That's why we associate to each AlgorithmEngine (which represents the instantiation of one algorithm) one of these AlgorithmExecutionContext.
 * 
 * Currently it stores the historical values for parameters requiring that.
 * 
 * Each execution context has a parent that stores the values which are not context specific.
 * 
 * @author nm
 *
 */
public class AlgorithmExecutionContext {
    // For storing a window of previous parameter instances
    HashMap<Parameter,WindowBuffer> buffersByParam = new HashMap<Parameter,WindowBuffer>();
    final AlgorithmExecutionContext parent;
    
    //all the algorithms that run in this context
    HashMap<Algorithm,AlgorithmExecutor> engineByAlgorithm=new HashMap<Algorithm,AlgorithmExecutor>();
    //name used for debugging
    final String contextName;
    
    final Processor yproc;
    
    public AlgorithmExecutionContext(String contextName, AlgorithmExecutionContext parent, Processor proc) {
        this.contextName = contextName;
        this.parent = parent;
        this.yproc = proc;
    }
    
    public void enableBuffer(Parameter param, int lookbackSize) {
        if(parent==null || param.getDataSource()==DataSource.COMMAND || param.getDataSource()==DataSource.COMMAND_HISTORY) {
            if(buffersByParam.containsKey(param)) {
                WindowBuffer buf = buffersByParam.get(param);
                buf.expandIfNecessary(lookbackSize+1);
            } else {
                buffersByParam.put(param, new WindowBuffer(lookbackSize+1));
            }
        } else {
            parent.enableBuffer(param, lookbackSize); 
        }
    }

    public void updateHistoryWindows(List<ParameterValue> pvals) {
        for(ParameterValue pval:pvals) {
            if(buffersByParam.containsKey(pval.getParameter())) {
                buffersByParam.get(pval.getParameter()).update(pval);
            } else if(parent!=null){
                parent.updateHistoryWindow(pval);
            }
        }
    }

    /**
     * 
     * @return the processor in which this context is part of
     */
    public Processor getProcessor() {
        return yproc;
    }
    private void updateHistoryWindow(ParameterValue pval) {
        if(buffersByParam.containsKey(pval.getParameter())) {
            buffersByParam.get(pval.getParameter()).update(pval);
        }
    }

    public ParameterValue getHistoricValue(ParameterInstanceRef pInstance) {
        WindowBuffer wb = buffersByParam.get(pInstance.getParameter());
        if(wb!=null) {
            return wb.getHistoricValue(pInstance.getInstance());
        } else if (parent!=null){
            return parent.getHistoricValue(pInstance);
        } else {
            return null;
        }
    }
    public String getName() {
        return contextName;
    }

    public boolean containsAlgorithm(Algorithm algo) {
        return engineByAlgorithm.containsKey(algo);
    }

    public AlgorithmExecutor getExecutor(Algorithm algo) {
        return engineByAlgorithm.get(algo);
    }


    public void addAlgorithm(Algorithm algorithm, AlgorithmExecutor engine) {
        engineByAlgorithm.put(algorithm, engine);
    }
    

    public Collection<Algorithm> getAlgorithms() {
        return engineByAlgorithm.keySet();
    }

    public AlgorithmExecutor remove(Algorithm algo) {
        return engineByAlgorithm.remove(algo);
    }
}
