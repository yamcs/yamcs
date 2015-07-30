package org.yamcs.commanding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.algorithms.AlgorithmManager;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.xtce.CheckWindow;
import org.yamcs.xtce.CheckWindow.TimeWindowIsRelativeToType;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.CommandVerifier.TerminationAction;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.SequenceContainer;

/**
 * This class implements the (post transmission) command verification.
 * 
 * There is one handler for all the verifiers of a command. 
 *  TODO: They share a common pool of data for parameter checks and for algorithms (i.e. one algorithm is run once inside this pool for all verifiers). 
 * 
 * @author nm
 *
 */
public class CommandVerificationHandler {
    final YProcessor yproc;
    final PreparedCommand preparedCommand;
    final ScheduledThreadPoolExecutor timer;
    private List<Verifier> pendingVerifiers = new ArrayList<Verifier>();
    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());
    enum VerifResult {OK, NOK, TIMEOUT};
    AlgorithmExecutionContext algorithmCtx;

    public CommandVerificationHandler(YProcessor yproc, PreparedCommand pc) {
        this.yproc = yproc;
        this.preparedCommand = pc;
        this.timer = yproc.getTimer();
    }


    public void start() {
        MetaCommand cmd = preparedCommand.getMetaCommand();
        List<CommandVerifier> verifiers = new ArrayList<CommandVerifier>();
        collectVerifiers(cmd, verifiers);
        Verifier prevVerifier = null;

        for(CommandVerifier cv: verifiers) {
            SequenceContainer c = cv.getContainerRef();
            Verifier verifier;

            if(c!=null) {
                verifier = new ContainerVerifier(this, cv, c);
            } else {
                if(algorithmCtx==null)  createAlgorithmContext(); 
                verifier = new AlgorithmVerifier(this, cv);
            }
            CheckWindow checkWindow = cv.getCheckWindow();
            boolean scheduleNow = true;

            if(checkWindow.getTimeWindowIsRelativeTo()==TimeWindowIsRelativeToType.timeLastVerifierPassed) {
                if(prevVerifier!=null) {
                    prevVerifier.nextVerifier = verifier;
                    scheduleNow = false;
                }
            };

            if(scheduleNow) {
                scheduleVerifier(verifier, checkWindow.getTimeToStartChecking(), checkWindow.getTimeToStopChecking());
            }

        }
    }

    private void collectVerifiers(MetaCommand cmd,  List<CommandVerifier> verifiers) {        
        for(CommandVerifier cv: cmd.getCommandVerifiers()) {
            boolean found = false;
            for(CommandVerifier existingv: verifiers) {
                if(existingv.getStage().equals(cv.getStage())) {
                    found = true;
                    break;
                }
            }
            if(!found) verifiers.add(cv);
        }
        MetaCommand basecmd = cmd.getBaseMetaCommand();
        if(basecmd!=null) {
            collectVerifiers(basecmd, verifiers);
        }
    }


    private void createAlgorithmContext() {
        AlgorithmManager algMgr = yproc.getParameterRequestManager().getParameterProvider(AlgorithmManager.class);
        if(algMgr==null) {
            String msg = "Algorithm manager not configured for this processor, cannot run command verification based on algorithms";
            log.error(msg);
            throw new RuntimeException(msg);
        }

        algorithmCtx = algMgr.createContext(preparedCommand.getCmdName());
    }


    private void scheduleVerifier(final Verifier verifier, long windowStart, long windowStop) {
        if(windowStart>0) {
            timer.schedule(new Runnable() {
                @Override
                public void run() {
                    verifier.start();
                }
            }, windowStart, TimeUnit.MILLISECONDS);
        } else {
            verifier.start();
        }
        pendingVerifiers.add(verifier);

        if(windowStop<=0) {
            throw new IllegalArgumentException("The window stop has to be greater than 0");
        }

        timer.schedule(new Runnable() {
            @Override
            public void run() {
                onVerifierFinished(verifier, VerifResult.TIMEOUT);
            }
        }, windowStop, TimeUnit.MILLISECONDS);
    }

    void onVerifierFinished(Verifier v, VerifResult result) {
        if(!pendingVerifiers.remove(v)) {
            if(result!=VerifResult.TIMEOUT) {
                log.warn("Got verifier finished for a verifier not in the pending list. cmd: "+preparedCommand.getCmdName()+" verifier:"+v.cv);
            }
            return;
        }
        
        log.debug("Command "+preparedCommand.getCmdName()+" verifier finished: "+v.cv+", result: "+result);
        CommandVerifier cv = v.cv;
        CommandHistoryPublisher cmdHistPublisher = yproc.getCommandHistoryPublisher();
        String histKey= CommandHistoryPublisher.Verifier_KEY_PREFIX+"_"+cv.getStage();
        cmdHistPublisher.updateStringKey(preparedCommand.getCommandId(), histKey, result.toString());
        TerminationAction ta = null;
        switch(result) {
        case OK:
            ta = cv.getOnSuccess();
            break;
        case NOK:
            ta = cv.getOnFail();
            break;
        case TIMEOUT:
            ta = cv.getOnTimeout();
            break;
        } 
        if(ta==TerminationAction.SUCCESS) {
            cmdHistPublisher.updateStringKey(preparedCommand.getCommandId(), CommandHistoryPublisher.CommandComplete_KEY, "OK");
        } else if(ta==TerminationAction.FAIL) {
            cmdHistPublisher.updateStringKey(preparedCommand.getCommandId(), CommandHistoryPublisher.CommandFailed_KEY, "NOK");
        }


        if(v.nextVerifier!=null && (result==VerifResult.OK)) {
            CheckWindow cw = v.nextVerifier.cv.getCheckWindow();
            scheduleVerifier(v.nextVerifier, cw.getTimeToStartChecking(), cw.getTimeToStopChecking());
        }
    }


    public YProcessor getProcessor() {
        return yproc;
    }

    public AlgorithmExecutionContext getAlgorithmExecutionContext() {
        return algorithmCtx;
    }

    public PreparedCommand getPreparedCommand() {
        return preparedCommand;
    }


    public AlgorithmManager getAlgorithmManager() {
        return yproc.getParameterRequestManager().getParameterProvider(AlgorithmManager.class);
    }
}
