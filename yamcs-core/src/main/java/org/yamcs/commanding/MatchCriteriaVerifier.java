package org.yamcs.commanding;

import java.util.List;

import org.yamcs.Processor;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.CriteriaEvaluator;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.MatchCriteria.MatchResult;
import org.yamcs.xtceproc.CriteriaEvaluatorImpl;

/**
 * Verifies commands by checking XTCE comparisons
 * 
 * @author nm
 *
 */
public class MatchCriteriaVerifier extends Verifier implements ParameterConsumer {
    final Processor proc;
    final MatchCriteria matchCriteria;
    int subscriptionId = -1;
    Log log;

    MatchCriteriaVerifier(CommandVerificationHandler cvh, CommandVerifier cv, Log log) {
        super(cvh, cv);
        this.proc = cvh.getProcessor();
        this.matchCriteria = cv.getMatchCriteria();
        this.log = log;
    }

    @Override
    void doStart() {
        try {
            subscriptionId = proc.getParameterRequestManager()
                    .addRequest(matchCriteria.getDependentParameters(), this);
        } catch (Exception e) {
            log.warn("Failed to subscribe to parameters", e);
        }
        check(new ParameterValueList());
    }

    private void check(ParameterValueList pvList) {
        if (state != State.RUNNING) {
            return;
        }
        CriteriaEvaluator condEvaluator = new CriteriaEvaluatorImpl(pvList, proc.getLastValueCache());
        MatchResult r = matchCriteria.matches(condEvaluator);
        log.debug("Condition check result: {}", r);

        // serialize the result in the timer, just in case two conflicting deliveries happen at the same time
        timer.execute(() -> {
            if (r == MatchResult.OK) {
                finishOK();
            } else if (r == MatchResult.NOK && cv.failOnFirstFailedMatch()) {
                finished(false, "Verifier condition does not match");
            }
        });
    }

    @Override
    void doCancel() {
        if (subscriptionId != 0) {
            proc.getParameterRequestManager().unsubscribeAll(subscriptionId);
        }
    }

    @Override
    public void updateItems(int subscriptionId, final List<ParameterValue> items) {
        ParameterValueList pvlist = new ParameterValueList(items);
        check(pvlist);
    }
}
