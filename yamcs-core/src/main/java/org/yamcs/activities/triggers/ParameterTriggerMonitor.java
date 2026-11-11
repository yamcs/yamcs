package org.yamcs.activities.triggers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.yamcs.Processor;
import org.yamcs.mdb.MatchCriteriaEvaluator;
import org.yamcs.mdb.MatchCriteriaEvaluator.MatchResult;
import org.yamcs.mdb.MatchCriteriaEvaluatorFactory;
import org.yamcs.mdb.ProcessingContext;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.activities.ParameterCondition;
import org.yamcs.protobuf.activities.ParameterCondition.Edge;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;

/**
 * Evaluates a boolean expression over parameters each time one of them is updated, and reports state changes that
 * match the configured edge.
 * <p>
 * All evaluation happens on the trigger service thread. The first known state after start only initializes the
 * monitor; it never fires.
 */
class ParameterTriggerMonitor implements TriggerMonitor, ParameterConsumer {

    private final TriggerService service;
    private final TriggerService.TriggerState state;
    private final Processor processor;
    private final ScheduledExecutorService executor;
    private final ParameterCondition condition;

    private MatchCriteriaEvaluator evaluator;
    private List<Parameter> parameters;
    private final Map<Parameter, ParameterValue> latestValues = new HashMap<>();

    private int subscriptionId = -1;
    private volatile boolean running;

    // Last known result of the expression (null if unknown)
    private Boolean lastResult;
    private ScheduledFuture<?> holdFuture;

    ParameterTriggerMonitor(TriggerService service, TriggerService.TriggerState state, Processor processor,
            ScheduledExecutorService executor) {
        this.service = service;
        this.state = state;
        this.processor = processor;
        this.executor = executor;
        this.condition = state.getDefinition().getParameterCondition();
    }

    @Override
    public void start() throws Exception {
        var parser = new ConditionExpressionParser(processor.getMdb());
        var expression = parser.parse(condition.getExpression());
        evaluator = MatchCriteriaEvaluatorFactory.getEvaluator(expression);
        parameters = new ArrayList<>(parser.getParameters());

        // Initialize from values already known by the processor
        var lvc = processor.getLastValueCache();
        if (lvc != null) {
            for (var parameter : parameters) {
                var pv = lvc.getValue(parameter);
                if (pv != null) {
                    latestValues.put(parameter, pv);
                }
            }
            update();
        }

        running = true;
        subscriptionId = processor.getParameterRequestManager().addRequest(parameters, this);
    }

    @Override
    public void stop() {
        running = false;
        if (subscriptionId != -1) {
            processor.getParameterRequestManager().removeRequest(subscriptionId);
            subscriptionId = -1;
        }
        cancelHold();
    }

    @Override
    public void updateItems(int subscriptionId, List<ParameterValue> items) {
        if (!running) {
            return;
        }
        var copy = new ArrayList<>(items);
        executor.execute(() -> {
            if (!running) {
                return;
            }
            for (var pv : copy) {
                latestValues.put(pv.getParameter(), pv);
            }
            update();
        });
    }

    private void update() {
        var result = evaluate();
        if (result == null) {
            return;
        }
        if (lastResult == null) {
            lastResult = result;
            return;
        }
        if (result.equals(lastResult)) {
            return;
        }

        lastResult = result;
        cancelHold();
        if (!edgeMatches(result)) {
            return;
        }

        var holdTime = condition.getHoldTime();
        if (holdTime > 0) {
            holdFuture = executor.schedule(() -> {
                holdFuture = null;
                if (running && result.equals(lastResult)) {
                    fire(result);
                }
            }, holdTime, TimeUnit.MILLISECONDS);
        } else {
            fire(result);
        }
    }

    /**
     * @return the expression result, or null if it cannot be determined
     */
    private Boolean evaluate() {
        var ctx = ProcessingContext.createForTmProcessing(new LastValueCache(), TimeEncoding.getWallclockTime());
        for (var parameter : parameters) {
            var pv = latestValues.get(parameter);
            if (pv == null) {
                return null;
            }
            if (pv.getAcquisitionStatus(true) != AcquisitionStatus.ACQUIRED) {
                return condition.getInvalidAsFalse() ? Boolean.FALSE : null;
            }
            ctx.addTmParam(pv);
        }

        var result = evaluator.evaluate(ctx);
        if (result == MatchResult.UNDEF) {
            return null;
        }
        return result == MatchResult.OK;
    }

    private boolean edgeMatches(boolean result) {
        var edge = condition.getEdge();
        return edge == Edge.BOTH
                || (edge == Edge.RISING && result)
                || (edge == Edge.FALLING && !result);
    }

    private void cancelHold() {
        if (holdFuture != null) {
            holdFuture.cancel(false);
            holdFuture = null;
        }
    }

    private void fire(boolean result) {
        var context = new LinkedHashMap<String, Object>();
        context.put("condition.expression", condition.getExpression());
        context.put("condition.edge", result ? Edge.RISING.name() : Edge.FALLING.name());
        context.put("processor", processor.getName());
        boolean first = true;
        for (var parameter : parameters) {
            var pv = latestValues.get(parameter);
            if (pv != null && pv.getEngValue() != null) {
                var value = TriggerService.toContextValue(pv.getEngValue());
                if (first) {
                    context.put("value", value);
                    context.put("parameter", parameter.getQualifiedName());
                    first = false;
                }
                context.put(ArgumentTemplates.PARAM_PREFIX + parameter.getQualifiedName(), value);
            }
        }
        service.onConditionMet(state, context);
    }
}
