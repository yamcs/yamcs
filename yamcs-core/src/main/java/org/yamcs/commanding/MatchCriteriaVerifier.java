package org.yamcs.commanding;

import java.util.List;
import java.util.stream.Collectors;

import org.yamcs.Processor;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtceproc.MatchCriteriaEvaluator;
import org.yamcs.xtceproc.MatchCriteriaEvaluator.MatchResult;
import org.yamcs.xtceproc.MatchCriteriaEvaluator.EvaluatorInput;

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
public class MatchCriteriaVerifier extends Verifier implements ParameterConsumer {
    final Processor proc;
    final MatchCriteria matchCriteria;
    int subscriptionId = -1;
    Log log;
    MatchCriteriaEvaluator evaluator;
    EvaluatorInput evaluatorInput;

    MatchCriteriaVerifier(CommandVerificationHandler cvh, CommandVerifier cv, Log log) {
        super(cvh, cv);
        this.proc = cvh.getProcessor();
        this.matchCriteria = cv.getMatchCriteria();
        this.log = log;
        this.evaluator = proc.getProcessorData().getEvaluator(cv.getMatchCriteria());
        PreparedCommand pc = cvh.getPreparedCommand();
        this.evaluatorInput = new EvaluatorInput(proc.getLastValueCache(), pc.getArgAssignment(),
                pc.getAttributesAsParameters(proc.getXtceDb()));
        this.evaluatorInput.setCmdHistParams(new ParameterValueList());
    }

    @Override
    void doStart() {
        try {
            List<Parameter> params = matchCriteria.getDependentParameters()
                    .stream()
                    .filter(p-> p.getDataSource()!=DataSource.COMMAND && p.getDataSource()!=DataSource.COMMAND_HISTORY)
                    .collect(Collectors.toList());

            subscriptionId = proc.getParameterRequestManager().addRequest(params, this);
        } catch (Exception e) {
            log.warn("Failed to subscribe to parameters", e);
        }
        check();
    }

    private void check() {
        if (state != State.RUNNING) {
            return;
        }
        MatchResult r = evaluator.evaluate(evaluatorInput);
        log.debug("Condition check result with parameters {}: {}", evaluatorInput, r);
        // serialize the result in the timer, just in case two conflicting deliveries happen at the same time
        timer.execute(() -> {
            unsubscribe();
            if (r == MatchResult.OK) {
                finishOK();
            } else if (r == MatchResult.NOK && cv.failOnFirstFailedMatch()) {
                finished(false, "Verifier condition does not match");
            }
        });
    }

    @Override
    void doCancel() {
        unsubscribe();
    }

    private synchronized void unsubscribe() {
        if (subscriptionId != -1) {
            proc.getParameterRequestManager().unsubscribeAll(subscriptionId);
            subscriptionId = -1;
        }
    }
    @Override
    public void updateItems(int subscriptionId, final List<ParameterValue> params) {
        evaluatorInput.setParams(new ParameterValueList(params));
        check();
    }

    public void updatedCommandHistoryParam(ParameterValue pv) {
        evaluatorInput.getCmdHistParams().add(pv);
    }

}
