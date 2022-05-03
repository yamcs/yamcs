package org.yamcs.algorithms;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.mdb.ProcessingData;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.OutputParameter;

public abstract class AbstractJavaExprExecutor extends AbstractAlgorithmExecutor {

    public AbstractJavaExprExecutor(Algorithm algorithmDef, AlgorithmExecutionContext execCtx) {
        super(algorithmDef, execCtx);
    }

    @Override
    public AlgorithmExecutionResult execute(long acqTime, long genTime, ProcessingData data) throws AlgorithmException {
        try {
            List<ParameterValue> outputValues = new ArrayList<>(algorithmDef.getOutputList().size());
            for (OutputParameter outputParam : algorithmDef.getOutputList()) {
                ParameterValue pv = new ParameterValue(outputParam.getParameter());
                pv.setGenerationTime(genTime);
                pv.setAcquisitionTime(acqTime);
                outputValues.add(pv);
            }
            Object returnValue = doExecute(acqTime, genTime, outputValues);

            // remove the values which have not been set
            int k = 0;
            for (ParameterValue pv : outputValues) {
                if (pv.getEngValue() != null || pv.getRawValue() != null) {
                    outputValues.set(k++, pv);
                }
            }
            outputValues.subList(k, outputValues.size()).clear();
            return new AlgorithmExecutionResult(inputValues, returnValue, outputValues);

        } catch (AlgorithmException e) {
            throw e;
        } catch (Exception e) {
            throw new AlgorithmException(e);
        }
    }

    protected abstract Object doExecute(long acqTime, long genTime, List<ParameterValue> outputValues);
}
