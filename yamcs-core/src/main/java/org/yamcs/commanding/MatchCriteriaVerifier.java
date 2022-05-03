package org.yamcs.commanding;

import java.util.Set;
import java.util.stream.Collectors;

import org.yamcs.Processor;
import org.yamcs.mdb.MatchCriteriaEvaluator;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.mdb.MatchCriteriaEvaluator.MatchResult;
import org.yamcs.parameter.ParameterProcessor;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.Parameter;

/**
 * Verifies commands by checking {@link MatchCriteria}. It implements the following XTCE verifier types:
 * <ul>
 * <li>ComparisonList</li>
 * <li>BooleanExpression</li>
 * <li>Comparison</li>
 * </ul>
 * 
 * @author nm
 *
 */
public class MatchCriteriaVerifier extends Verifier implements ParameterProcessor {
    final Processor proc;
    final MatchCriteria matchCriteria;

    MatchCriteriaEvaluator evaluator;
    int ppmSubscriptionId = -1;

    MatchCriteriaVerifier(CommandVerificationHandler cvh, CommandVerifier cv) {
        super(cvh, cv);

        this.proc = cvh.getProcessor();
        this.matchCriteria = cv.getMatchCriteria();

        this.evaluator = proc.getProcessorData().getEvaluator(cv.getMatchCriteria());

    }

    @Override
    void doStart() {
        Set<Parameter> pset = matchCriteria.getDependentParameters().stream()
                .filter(p -> !p.isCommandParameter()).collect(Collectors.toSet());
        if (pset != null) {
            ppmSubscriptionId = proc.getParameterProcessorManager().subscribe(pset, this);
        }
        ActiveCommand cmd = cvh.getActiveCommand();
        check(ProcessingData.createInitial(proc.getLastValueCache(), cmd.getArguments(), cmd.getCmdParamCache()));
    }

    @Override
    void doCancel() {
        unsubscribe();
    }

    @Override
    public void process(ProcessingData tmData) {
        ProcessingData cmdData = ProcessingData.cloneForCommanding(tmData, activeCommand.getArguments(),
                activeCommand.getCmdParamCache());
        check(cmdData);
    }

    private void check(ProcessingData processingData) {
        if (state != State.RUNNING) {
            return;
        }
        MatchResult result = evaluator.evaluate(processingData);
        log.debug("Condition check result: {}", result);

        if (result == MatchResult.UNDEF ||
                (result == MatchResult.NOK && !cv.failOnFirstFailedMatch())) {
            return;
        }

        // if there is a value for the return parameter in the current evaluatorInput (the one from which the result
        // has been computed), we want that one to be the returnValue
        ParameterValue pv = getReturnValue(processingData);

        timer.submit(() -> {
            if (state != State.RUNNING) {// it was finished in a different thread
                return;
            }
            returnPv = pv;
            if (result == MatchResult.OK) {
                unsubscribe();
                finishOK();
            } else if (result == MatchResult.NOK) {
                unsubscribe();
                finished(false, "Verifier condition does not match");
            }
        });
    }

    private ParameterValue getReturnValue(ProcessingData processingData) {
        Parameter returnParam = cv.getReturnParameter();
        if (returnParam == null) {
            return null;
        }
        ParameterValue retPv;
        if (returnParam.getDataSource() == DataSource.COMMAND_HISTORY
                || returnParam.getDataSource() == DataSource.COMMAND) {
            retPv = processingData.getCmdParams().getLastInserted(returnParam);
        } else {
            retPv = processingData.getTmParams().getLastInserted(returnParam);
        }
        return retPv;
    }

    private synchronized void unsubscribe() {
        if (ppmSubscriptionId != -1) {
            proc.getParameterProcessorManager().unsubscribe(ppmSubscriptionId);
        }
    }

}
