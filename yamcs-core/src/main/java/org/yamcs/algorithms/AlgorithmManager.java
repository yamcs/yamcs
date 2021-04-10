package org.yamcs.algorithms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import org.yamcs.AbstractProcessorService;
import org.yamcs.InvalidIdentification;
import org.yamcs.InvalidRequestIdentification;
import org.yamcs.Processor;
import org.yamcs.ProcessorService;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.ArgumentValue;
import org.yamcs.events.EventProducer;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterListener;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.protobuf.AlgorithmStatus;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.MathAlgorithm;
import org.yamcs.xtce.NamedDescriptionIndex;
import org.yamcs.xtce.OnPeriodicRateTrigger;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.TriggerSetType;
import org.yamcs.xtce.XtceDb;

import com.google.common.collect.Lists;
import com.google.protobuf.util.Timestamps;

/**
 * Manages the provision of requested parameters that require the execution of one or more XTCE algorithms.
 * <p>
 * Upon initialization it will scan all algorithms, and schedule any that are to be triggered periodically.
 * OutputParameters of all algorithms will be indexed, so that AlgorithmManager knows what parameters it can provide to
 * the ParameterRequestManager.
 * <p>
 * Algorithms and any needed algorithms that require earlier execution, will be activated as soon as a request for one
 * of its output parameters is registered.
 * <p>
 * Algorithm executors are created by {@link AlgorithmExecutorFactory} which themselves are created by the
 * {@link AlgorithmEngine}. The algorithm engines are registered at server startup using the
 * {@link #registerAlgorithmEngine(String, AlgorithmEngine)} method.
 *
 * javascript will be automatically registered as well as python if available.
 */
public class AlgorithmManager extends AbstractProcessorService
        implements ParameterProvider, ProcessorService {
    static final String KEY_ALGO_NAME = "algoName";

    XtceDb xtcedb;

    // Index of all available out params
    NamedDescriptionIndex<Parameter> outParamIndex = new NamedDescriptionIndex<>();

    CopyOnWriteArrayList<ActiveAlgorithm> executionOrder = new CopyOnWriteArrayList<>();
    HashSet<Parameter> requiredInParams = new HashSet<>(); // required by this class
    ArrayList<Parameter> requestedOutParams = new ArrayList<>(); // requested by clients
    ParameterRequestManager parameterRequestManager;

    // this stores the algorithms which give an error at activation
    Map<Algorithm, AlgorithmStatus> algorithmsInError = new HashMap<>();

    // For scheduling OnPeriodicRate algorithms
    ScheduledExecutorService timer;
    AlgorithmExecutionContext globalCtx;

    EventProducer eventProducer;

    final static Map<String, AlgorithmEngine> algorithmEngines = new HashMap<>();

    // language -> algorithm factory
    final Map<String, AlgorithmExecutorFactory> factories = new HashMap<>();

    final Map<CustomAlgorithm, CustomAlgorithm> algoOverrides = new HashMap<>();

    static {
        registerScriptEngines();
        registerAlgorithmEngine("Java", new JavaAlgorithmEngine());
    }

    /**
     * Create a ScriptEngineManager for each of the script engines available in the jre.
     */
    private static void registerScriptEngines() {
        ScriptEngineManager sem = new ScriptEngineManager();
        for (ScriptEngineFactory sef : sem.getEngineFactories()) {
            List<String> engineNames = sef.getNames();
            ScriptAlgorithmEngine engine = new ScriptAlgorithmEngine();
            for (String name : engineNames) {
                registerAlgorithmEngine(name, engine);
            }
        }
    }

    public static void registerAlgorithmEngine(String name, AlgorithmEngine eng) {
        algorithmEngines.put(name, eng);
    }

    @Override
    public void init(Processor processor, YConfiguration config, Object spec) {
        super.init(processor, config, spec);

        this.eventProducer = processor.getProcessorData().getEventProducer();
        this.parameterRequestManager = processor.getParameterRequestManager();

        this.parameterRequestManager.addParameterProvider(this);
        this.parameterRequestManager.setAlgortihmManager(this);

        xtcedb = processor.getXtceDb();
        timer = processor.getTimer();

        globalCtx = new AlgorithmExecutionContext("global", null, processor.getProcessorData());

        for (Algorithm algo : xtcedb.getAlgorithms()) {
            if (algo.getScope() == Algorithm.Scope.GLOBAL) {
                loadAlgorithm(algo, globalCtx);
            }
        }
    }

    private void loadAlgorithm(Algorithm algo, AlgorithmExecutionContext ctx) {
        for (OutputParameter oParam : algo.getOutputSet()) {
            outParamIndex.add(oParam.getParameter());
        }
        // Eagerly activate the algorithm if no outputs (with lazy activation,
        // it would never trigger because there's nothing to subscribe to)
        if (algo.getOutputSet().isEmpty() && !ctx.containsAlgorithm(algo)) {
            activateAlgorithm(algo, ctx, null);
        }
        TriggerSetType tst = algo.getTriggerSet();
        if (tst == null) {
            eventProducer.sendWarning("No trigger set for algorithm '" + algo.getQualifiedName() + "'");
        } else {
            List<OnPeriodicRateTrigger> timedTriggers = tst.getOnPeriodicRateTriggers();
            if (!timedTriggers.isEmpty()) {
                // acts as a fixed-size pool
                activateAlgorithm(algo, ctx, null);
                final ActiveAlgorithm activeAlgo = ctx.getActiveAlgorithm(algo);
                if (activeAlgo != null) {
                    for (OnPeriodicRateTrigger trigger : timedTriggers) {
                        timer.scheduleAtFixedRate(() -> {
                            long t = processor.getCurrentTime();
                            List<ParameterValue> params = activeAlgo.runAlgorithm(t, t);
                            parameterRequestManager.update(new ParameterValueList(params));
                        }, 1000, trigger.getFireRate(), TimeUnit.MILLISECONDS);
                    }
                }
            }
        }
    }

    @Override
    public void startProviding(Parameter paramDef) {
        if (requestedOutParams.contains(paramDef)) {
            return;
        }

        for (Algorithm algo : xtcedb.getAlgorithms()) {
            for (OutputParameter oParam : algo.getOutputSet()) {
                if (oParam.getParameter() == paramDef) {
                    activateAlgorithm(algo, globalCtx, null);
                    requestedOutParams.add(paramDef);
                    return; // There shouldn't be more ...
                }
            }
        }
    }

    /**
     * Create a new algorithm execution context.
     *
     * @param name
     *            - name of the context
     * @return the newly created context
     */
    public AlgorithmExecutionContext createContext(String name) {
        return new AlgorithmExecutionContext(name, globalCtx, processor.getProcessorData());
    }

    /**
     * Activate an algorithm in a context if not already active.
     *
     * If already active, the listener is added to the listener list.
     *
     * @param algorithm
     * @param execCtx
     * @param listener
     */
    public void activateAlgorithm(Algorithm algorithm, AlgorithmExecutionContext execCtx,
            AlgorithmExecListener listener) {
        ActiveAlgorithm activeAlgo = execCtx.getActiveAlgorithm(algorithm);
        if (activeAlgo != null) {
            log.trace("Already activated algorithm {} in context {}", algorithm.getQualifiedName(), execCtx.getName());
            if (listener != null) {
                activeAlgo.addExecListener(listener);
            }
            return;
        }

        AlgorithmExecutor executor;

        log.trace("Activating algorithm....{}", algorithm.getQualifiedName());
        if (algorithm instanceof CustomAlgorithm) {
            CustomAlgorithm calg = (CustomAlgorithm) algorithm;
            String algLang = calg.getLanguage();
            if (algLang == null) {
                eventProducer
                        .sendCritical("no language specified for algorithm '" + algorithm.getQualifiedName() + "'");
                return;
            }
            AlgorithmExecutorFactory factory = factories.get(algLang);
            if (factory == null) {
                AlgorithmEngine eng = algorithmEngines.get(algLang);
                if (eng == null) {
                    eventProducer.sendCritical("no algorithm engine found for language '" + algLang + "'");
                    return;
                }
                factory = eng.makeExecutorFactory(this, execCtx, algLang, config);
                factories.put(algLang, factory);
                for (String s : factory.getLanguages()) {
                    factories.put(s, factory);
                }
            }
            try {
                executor = factory.makeExecutor(calg, execCtx);
            } catch (AlgorithmException e) {
                log.warn("Failed to create algorithm executor", e);
                eventProducer.sendCritical("Failed to create executor for algorithm "
                        + calg.getQualifiedName() + ": " + e);
                AlgorithmStatus.Builder status = AlgorithmStatus.newBuilder()
                        .setErrorMessage(e.getMessage())
                        .setErrorTime(Timestamps.fromMillis(System.currentTimeMillis()));
                algorithmsInError.put(algorithm, status.build());
                return;
            }
        } else if (algorithm instanceof MathAlgorithm) {
            executor = new MathAlgorithmExecutor(algorithm, execCtx, (MathAlgorithm) algorithm);
        } else {
            throw new UnsupportedOperationException(
                    "Algorithms of type " + algorithm.getClass() + " not yet implemented");
        }

        activeAlgo = new ActiveAlgorithm(algorithm, execCtx, executor);
        if (listener != null) {
            activeAlgo.addExecListener(listener);
        }
        execCtx.addAlgorithm(activeAlgo);

        // last value cache will contain the latest known values for all parameters
        // including the initialValue
        LastValueCache lvc = processor.getLastValueCache();
        if (lvc != null) {
            executor.updateParameters(new ParameterValueList(lvc.getValues()));
        }

        try {
            ArrayList<Parameter> newItems = new ArrayList<>();
            for (Parameter param : getParametersOfInterest(algorithm)) {
                if (!requiredInParams.contains(param)) {
                    requiredInParams.add(param);
                    // Recursively activate other algorithms on which this algorithm depends
                    if (canProvide(param)) {
                        for (Algorithm algo : xtcedb.getAlgorithms()) {
                            if (algorithm != algo) {
                                for (OutputParameter oParam : algo.getOutputSet()) {
                                    if (oParam.getParameter() == param) {
                                        activateAlgorithm(algo, execCtx, null);
                                    }
                                }
                            }
                        }
                    } else { // Don't ask items to PRM that we can provide ourselves or command verifier context
                        // parameters that PRM cannot provide
                        if ((param.getDataSource() != DataSource.COMMAND)
                                && param.getDataSource() != DataSource.COMMAND_HISTORY) {
                            newItems.add(param);
                        }
                    }
                }

                // Initialize a new Windowbuffer, or extend an existing one, if the algorithm requires
                // going back in time
                int lookbackSize = AlgorithmUtils.getLookbackSize(algorithm, param);
                if (lookbackSize > 0) {
                    boolean enabled = execCtx.enableBuffer(param, lookbackSize);
                    ParameterValue pv;
                    if (enabled && lvc != null && (pv = lvc.getValue(param)) != null) {
                        execCtx.updateHistoryWindow(pv);
                    }
                }
            }
            if (!newItems.isEmpty()) {
                parameterRequestManager.subscribeToProviders(newItems);
            }
            executionOrder.add(activeAlgo); // Add at the back (dependent algorithms will come in front)
        } catch (InvalidRequestIdentification e) {
            log.error("InvalidRequestIdentification caught when subscribing to the items required for the algorithm {}",
                    executor.getAlgorithm().getName(), e);
        }
    }

    public void deactivateAlgorithm(Algorithm algorithm, AlgorithmExecutionContext execCtx) {
        ActiveAlgorithm activeAlgo = execCtx.remove(algorithm);
        if (activeAlgo != null) {
            executionOrder.remove(activeAlgo);
        }
    }

    @Override
    public void startProvidingAll() {
        for (Parameter p : outParamIndex.getObjects()) {
            startProviding(p);
        }
    }

    @Override
    public void stopProviding(Parameter paramDef) {
        if (requestedOutParams.remove(paramDef)) {
            // Remove active algorithm (and any that are no longer needed as a consequence)
            // We need to clean-up three more internal structures: requiredInParams, executionOrder and
            // engineByAlgorithm
            HashSet<Parameter> stillRequired = new HashSet<>(); // parameters still required by any other algorithm
            for (Iterator<ActiveAlgorithm> it = Lists.reverse(executionOrder).iterator(); it.hasNext();) {
                ActiveAlgorithm engine = it.next();
                Algorithm algo = engine.getAlgorithm();
                boolean keep = false;

                // Keep if any other output parameters are still subscribed to
                for (OutputParameter oParameter : algo.getOutputSet()) {
                    if (requestedOutParams.contains(oParameter.getParameter())) {
                        keep = true;
                        break;
                    }
                }

                if (!algo.canProvide(paramDef)) { // Clean-up unused engines
                    // For any of its outputs, check if it's still used by any algorithm
                    for (OutputParameter op : algo.getOutputSet()) {
                        if (requestedOutParams.contains(op.getParameter())) {
                            keep = true;
                            break;
                        }
                        for (Algorithm otherAlgo : globalCtx.getAlgorithms()) {
                            if (getParametersOfInterest(otherAlgo).contains(op.getParameter())) {
                                keep = true;
                                break;
                            }
                        }
                    }
                }

                if (!keep) {
                    it.remove();
                    globalCtx.remove(algo);
                } else {
                    stillRequired.addAll(getParametersOfInterest(algo));
                }
            }
            requiredInParams.retainAll(stillRequired);
        }
    }

    @Override
    public boolean canProvide(Parameter p) {
        return (outParamIndex.get(p.getQualifiedName()) != null);
    }

    @Override
    public boolean canProvide(NamedObjectId itemId) {
        try {
            getParameter(itemId);
        } catch (InvalidIdentification e) {
            return false;
        }
        return true;
    }

    @Override
    public Parameter getParameter(NamedObjectId paraId) throws InvalidIdentification {
        Parameter p;
        if (paraId.hasNamespace()) {
            p = outParamIndex.get(paraId.getNamespace(), paraId.getName());
        } else {
            p = outParamIndex.get(paraId.getName());
        }
        if (p != null) {
            return p;
        } else {
            throw new InvalidIdentification();
        }
    }

    public void updateDelivery(ParameterValueList pvList) {
        updateDelivery(pvList, globalCtx);
    }

    /**
     * Update parameters in context and run the affected algorithms
     *
     * <p>
     * Add the result of the algorithms to the currentDelivery
     * 
     * @param currentDelivery
     * @param ctx
     */
    public void updateDelivery(ParameterValueList currentDelivery, AlgorithmExecutionContext ctx) {
        ctx.updateHistoryWindows(currentDelivery);
        long acqTime = processor.getCurrentTime();
        long genTime = currentDelivery.getFirst().getGenerationTime();

        for (ActiveAlgorithm activeAlgo : executionOrder) {
            if (ctx == globalCtx || activeAlgo.getExecutionContext() == ctx) {
                boolean shouldRun = activeAlgo.updateParameters(currentDelivery);
                if (shouldRun) {
                    List<ParameterValue> r = activeAlgo.runAlgorithm(acqTime, genTime);
                    if (r != null) {
                        currentDelivery.addAll(r);
                        ctx.updateHistoryWindows(new ParameterValueList(r));
                    }
                }
            }
        }
    }

    public void processArguments(Map<Argument, ArgumentValue> args, AlgorithmExecutionContext ctx) {
        long time = processor.getCurrentTime();
        ParameterValueList pvlist = new ParameterValueList();

        for (ActiveAlgorithm activeAlgo : executionOrder) {
            if (ctx == globalCtx || activeAlgo.getExecutionContext() == ctx) {
                boolean shouldRun = activeAlgo.updateArguments(args);
                if (shouldRun) {
                    List<ParameterValue> r = activeAlgo.runAlgorithm(time, time);
                    if (r != null) {
                        pvlist.addAll(r);
                        ctx.updateHistoryWindows(new ParameterValueList(r));
                    }
                }
            }
        }
        if (!pvlist.isEmpty()) {
            parameterRequestManager.update(pvlist);
        }
    }

    @Override
    public void setParameterListener(ParameterListener parameterRequestManager) {
        // do nothing, we're more interested in a ParameterRequestManager, which we're
        // getting from the constructor
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        if (timer != null) {
            timer.shutdownNow();
        }
        notifyStopped();
    }

    public Processor getProcessor() {
        return processor;
    }

    public void clearAlgorithmOverride(CustomAlgorithm calg) {
        CustomAlgorithm algOverr = algoOverrides.remove(calg);
        if (algOverr == null) {
            return;
        }
        deactivateAlgorithm(algOverr, globalCtx);
        activateAlgorithm(calg, globalCtx, null);
    }

    /**
     * Override the algorithm
     *
     * @param calg
     * @param text
     */
    public void overrideAlgorithm(CustomAlgorithm calg, String text) {
        CustomAlgorithm algOverr = algoOverrides.remove(calg);
        if (algOverr == null) {
            deactivateAlgorithm(calg, globalCtx);
        } else {
            deactivateAlgorithm(algOverr, globalCtx);
        }

        AlgorithmExecutorFactory factory = factories.get(calg.getLanguage());
        if (factory == null) {
            throw new AlgorithmException(
                    "No factory available for algorithms with language '" + calg.getLanguage() + "'");
        }
        algOverr = calg.copy();
        algOverr.setAlgorithmText(text);

        try {
            AlgorithmExecutor executor = factory.makeExecutor(algOverr, globalCtx);
            ActiveAlgorithm activeAlgo = new ActiveAlgorithm(algOverr, globalCtx, executor);
            globalCtx.addAlgorithm(activeAlgo);
            executionOrder.add(activeAlgo);

        } catch (AlgorithmException e) {
            log.warn("Failed to create algorithm executor", e);
            eventProducer.sendCritical("Failed to create executor for algorithm "
                    + algOverr.getQualifiedName() + ": " + e);
            AlgorithmStatus.Builder status = AlgorithmStatus.newBuilder()
                    .setErrorMessage(e.getMessage())
                    .setErrorTime(Timestamps.fromMillis(System.currentTimeMillis()));
            algorithmsInError.put(algOverr, status.build());
            return;
        }

        algoOverrides.put(calg, algOverr);
    }

    public void enableTracing(Algorithm algo) {
        log.debug("Enabling tracing for algorithm {}", algo);
        globalCtx.enableTracing(algo);
    }

    public void disableTracing(Algorithm algo) {
        log.debug("Disabling tracing for algorithm {}", algo);
        globalCtx.disableTracing(algo);
    }

    public AlgorithmTrace getTrace(Algorithm algo) {
        return globalCtx.getTrace(algo);
    }

    public AlgorithmStatus getAlgorithmStatus(Algorithm algo) {
        AlgorithmStatus status = algorithmsInError.get(algo);
        return status == null ? globalCtx.getAlgorithmStatus(algo) : status;
    }

    /**
     * Returns all the parameters that this algorithm want to receive updates on. This includes not only the input
     * parameters, but also any parameters that are part of the trigger set.
     */
    private static Set<Parameter> getParametersOfInterest(Algorithm algorithm) {
        Stream<Parameter> inputParams = algorithm.getInputList().stream()
                .filter(ip -> ip.getParameterInstance() != null).map(ip -> ip.getParameterInstance()
                        .getParameter());
        if (algorithm.getTriggerSet() == null) {
            return inputParams.collect(Collectors.toSet());
        } else {
            Stream<Parameter> triggerParams = algorithm.getTriggerSet().getOnParameterUpdateTriggers().stream()
                    .map(t -> t.getParameter());
            return Stream.concat(triggerParams, inputParams).collect(Collectors.toSet());
        }
    }

}
