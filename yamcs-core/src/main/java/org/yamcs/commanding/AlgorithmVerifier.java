package org.yamcs.commanding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.YProcessor;
import org.yamcs.algorithms.AlgorithmExecListener;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.algorithms.AlgorithmManager;
import org.yamcs.cmdhistory.CommandHistoryConsumer;
import org.yamcs.commanding.CommandVerificationHandler.VerifResult;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SystemParameterDb;
import org.yamcs.xtce.XtceDb;

public class AlgorithmVerifier extends Verifier implements AlgorithmExecListener, CommandHistoryConsumer {
    final Algorithm alg;
    final AlgorithmExecutionContext algCtx;
    final PreparedCommand pc;
    Logger log;
    final XtceDb xtcedb;

    final YProcessor yproc;
    AlgorithmVerifier(CommandVerificationHandler cvh, CommandVerifier cv) {
        super(cvh, cv);
        alg = cv.getAlgorithm();
        algCtx= cvh.getAlgorithmExecutionContext();
        pc = cvh.getPreparedCommand();
        yproc = cvh.getProcessor();
        xtcedb = yproc.getXtceDb();
        log=LoggerFactory.getLogger(AlgorithmVerifier.class);
    }

    @Override
    void start() {
        log.debug("Starting verifier "+cv.getStage()+" with the algorithm "+alg.getName());
        //push all the command information as parameters
        List<ParameterValue> pvList = new ArrayList<ParameterValue>();

        for(CommandHistoryAttribute cha: pc.getAttributes()) {
            String fqn = SystemParameterDb.YAMCS_CMD_SPACESYSTEM_NAME+"/"+cha.getName();
            if(!xtcedb.getSystemParameterDb().isDefined(fqn)) {
              //if it was required in the algorithm, it would be already in the system parameter db  
                log.debug("Not adding "+fqn+" to the context parameter list because it is not defined in the SystemParameterDb");
                continue;
            }
            Parameter p = xtcedb.getSystemParameterDb().getSystemParameter(fqn, true);

            ParameterValue pv = new ParameterValue(p);
            pv.setEngineeringValue(ValueUtility.fromGpb(cha.getValue()));
            pvList.add(pv);
        }
        Map<Argument, Value> argAssignment = pc.getArgAssignment();
        for(Map.Entry<Argument, Value> e: argAssignment.entrySet()) {
            String fqn = SystemParameterDb.YAMCS_CMD_SPACESYSTEM_NAME+"/arg/"+e.getKey().getName();
            if(!xtcedb.getSystemParameterDb().isDefined(fqn)) {
                //if it was required in the algorithm, it would be already in the SystemParameterdb  
                log.debug("Not adding "+fqn+" to the context parameter list because it is not defined in the SystemParameterDb");
                continue;
            }
            Parameter p =  xtcedb.getSystemParameterDb().getSystemParameter(fqn, true);

            ParameterValue pv = new ParameterValue(p);
            pv.setEngineeringValue(e.getValue());
            pvList.add(pv);
        }


        if(pvList.isEmpty()) {
            log.debug("No CMD information PV to be sent to the Algorithm");
        } else {
            AlgorithmManager algMgr = cvh.getAlgorithmManager();
            algMgr.activateAlgorithm(alg, algCtx, this);
            algMgr.updateParameters(pvList, algCtx);
        }
        try {
            yproc.getCommandHistoryManager().subscribeCommand(pc.getCommandId(), this);
        } catch (InvalidCommandId e) {
            log.error("Got invalidCommand id while subscribing for command history", e);
        }
    }

    @Override
    public void algorithmRun(Object returnValue, List<ParameterValue> outputValues) {
        if(returnValue==null) {
            log.trace("Algorithm {} run but did not return a result.", alg.getName());
            return;
        }
        if(!(returnValue instanceof Boolean)) {
            log.warn("Algorithm {} run but returned a {} instead of a Boolean", alg.getName(), returnValue.getClass());
            return;
        }
        boolean r = (Boolean) returnValue;
        AlgorithmManager algMgr = cvh.getAlgorithmManager();
        algMgr.deactivateAlgorithm(alg, algCtx);
        yproc.getCommandHistoryManager().unsubscribeCommand(pc.getCommandId(), this);
        
        cvh.onVerifierFinished(this, r?VerifResult.OK:VerifResult.NOK);
    }

    @Override
    public void addedCommand(PreparedCommand pc) {} //this will not be called because we subscribe to only one command

    
    //called from the command history when things are added in the stream
    @Override
    public void updatedCommand(CommandId cmdId, long changeDate, String key, Value value) {
        String fqn = SystemParameterDb.YAMCS_CMDHIST_SPACESYSTEM_NAME+"/"+key;
        if(!xtcedb.getSystemParameterDb().isDefined(fqn)) {
            //if it was required in the algorithm, it would be in the SystemParameterDb  
            log.debug("Not adding "+fqn+" to the context parameter list because it is not defined in the SystemParameterDb");
        } else {            
            Parameter p = xtcedb.getSystemParameterDb().getSystemParameter(fqn, true);
            ParameterValue pv = new ParameterValue(p);
            pv.setEngineeringValue(value);
            AlgorithmManager algMgr = cvh.getAlgorithmManager();
            algMgr.updateParameters(Arrays.asList(pv), algCtx);
        }
    }
}
