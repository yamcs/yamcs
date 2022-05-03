package org.yamcs.commanding;

import java.util.List;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.RawEngValue;
import org.yamcs.Processor;
import org.yamcs.algorithms.ActiveAlgorithm;
import org.yamcs.algorithms.AlgorithmExecListener;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.algorithms.AlgorithmManager;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.XtceDb;

public class AlgorithmVerifier extends Verifier implements AlgorithmExecListener {
    final Algorithm alg;
    final AlgorithmExecutionContext algCtx;
    final XtceDb xtcedb;

    final Processor processor;

    AlgorithmVerifier(CommandVerificationHandler cvh, CommandVerifier cv) {
        super(cvh, cv);
        alg = cv.getAlgorithm();
        algCtx = cvh.getAlgorithmExecutionContext();
        processor = cvh.getProcessor();
        xtcedb = processor.getXtceDb();
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
    public void algorithmRun(List<RawEngValue> inputValues, Object returnValue, List<ParameterValue> outputValues) {
        if (log.isTraceEnabled()) {
            CommandId cmdId = activeCommand.getCommandId();
            log.trace("command: {} algorithm {} stage{} executed: returnValue: {} , outputValues: {}",
                    StringConverter.toString(cmdId), alg.getName(), cv.getStage(), returnValue, outputValues);
        }
        if (returnValue == null) {
            log.trace("Algorithm {} run but did not return a result.", alg.getName());
            return;
        }
        algCtx.removeAlgorithm(alg.getQualifiedName());

        if (returnValue instanceof Boolean) {
            finished((Boolean) returnValue);
        } else {
            finished(false, returnValue.toString());
        }
    }

    public void processCmdData(ProcessingData data) {
        algCtx.process(processor.getCurrentTime(), data);
    }
}
