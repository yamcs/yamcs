package org.yamcs.commanding;

import java.util.List;

import org.yamcs.Processor;
import org.yamcs.algorithms.ActiveAlgorithm;
import org.yamcs.algorithms.AlgorithmExecListener;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.algorithms.AlgorithmManager;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.RawEngValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.CommandVerifier;

public class AlgorithmVerifier extends Verifier implements AlgorithmExecListener {

    final Algorithm alg;
    final AlgorithmExecutionContext algCtx;
    final Mdb mdb;

    final Processor processor;

    AlgorithmVerifier(CommandVerificationHandler cvh, CommandVerifier cv) {
        super(cvh, cv);
        alg = cv.getAlgorithm();
        algCtx = cvh.getAlgorithmExecutionContext();
        processor = cvh.getProcessor();
        mdb = processor.getMdb();
    }

    @Override
    void doStart() {
        activeCommand.subscribeCmdParams(data -> processCmdData(data));

        log.debug("Starting verifier for command {} alg: {} stage: {} ",
                StringConverter.toString(activeCommand.getCommandId()), alg.getName(), cv.getStage());
        AlgorithmManager algMgr = cvh.getAlgorithmManager();
        ActiveAlgorithm algo = algMgr.activateAlgorithm(alg, algCtx);
        if (algo == null) {
            log.warn("{}: failing verifier {} because algorithm could not be activated",
                    activeCommand.getCommandId(), cv.getStage());
            finished(false, "algorithm activation failed");
        } else {
            algo.addExecListener(this);
        }

        ProcessingData data = ProcessingData.createInitial(processor.getLastValueCache(),
                activeCommand.getArguments(), activeCommand.getCmdParamCache());
        // send initial values to algorithm
        algCtx.process(processor.getCurrentTime(), data);
    }

    @Override
    void doCancel() {
        algCtx.removeAlgorithm(alg.getQualifiedName());
    }

    @Override
    public void algorithmRun(List<RawEngValue> inputValues, Object result, List<ParameterValue> outputValues) {
        if (log.isTraceEnabled()) {
            CommandId cmdId = activeCommand.getCommandId();
            log.trace("command: {} algorithm: {} stage: {} executed: returnValue: {} , outputValues: {}",
                    StringConverter.toString(cmdId), alg.getName(), cv.getStage(), result, outputValues);
        }
        if (result == null) {
            log.trace("Algorithm {} run but did not return a result.", alg.getName());
            return;
        }
        algCtx.removeAlgorithm(alg.getQualifiedName());

        if (result instanceof Boolean) {
            finished((Boolean) result);
        } else if (result instanceof VerificationResult) {
            var verificationResult = (VerificationResult) result;
            if (verificationResult.returnValue != null) {
                var value = verificationResult.returnValue;
                returnPv = new ParameterValue(YAMCS_PARAMETER_RETURN_VALUE);
                var time = processor.getCurrentTime();
                returnPv.setAcquisitionTime(time);
                returnPv.setGenerationTime(time);
                if (value instanceof Value) {
                    returnPv.setEngValue((Value) value);
                } else {
                    var strValue = ValueUtility.getStringValue(value.toString());
                    returnPv.setEngValue(strValue);
                }
            }
            finished(verificationResult.success, verificationResult.message);
        } else {
            finished(false, result.toString());
        }
    }

    public void processCmdData(ProcessingData data) {
        algCtx.process(processor.getCurrentTime(), data);
    }
}
