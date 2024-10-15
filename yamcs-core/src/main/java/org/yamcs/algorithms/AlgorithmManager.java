package org.yamcs.algorithms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import org.yamcs.AbstractProcessorService;
import org.yamcs.InvalidIdentification;
import org.yamcs.Processor;
import org.yamcs.ProcessorService;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducer;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.parameter.ParameterProcessor;
import org.yamcs.parameter.ParameterProcessorManager;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.AlgorithmStatus;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.MathAlgorithm;
import org.yamcs.xtce.NamedDescriptionIndex;
import org.yamcs.xtce.OnPeriodicRateTrigger;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.TriggerSetType;
import org.yamcs.mdb.Mdb;

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
        implements ParameterProvider, ProcessorService, ParameterProcessor {
    static final String KEY_ALGO_NAME = "algoName";
    static final String JDK_BUILTIN_NASHORN_ENGINE_NAME = "Oracle Nashorn";

    Mdb mdb;

    // Index of all available out params
    NamedDescriptionIndex<Parameter> outParamIndex = new NamedDescriptionIndex<>();

    HashSet<Parameter> requiredInParams = new HashSet<>(); // required by this class
    ArrayList<Parameter> requestedOutParams = new ArrayList<>(); // requested by clients
    ParameterProcessorManager parameterProcessorManager;

    // this stores the algorithms which give an error at activation
    Map<String, AlgorithmStatus> algorithmsInError = new HashMap<>();

    // For scheduling OnPeriodicRate algorithms
    ScheduledExecutorService timer;
    AlgorithmExecutionContext globalCtx;

    EventProducer eventProducer;

    final static Map<String, AlgorithmEngine> algorithmEngines = new HashMap<>();

    // language -> algorithm factory
    final Map<String, AlgorithmExecutorFactory> factories = new HashMap<>();

    final Map<CustomAlgorithm, CustomAlgorithm> algoOverrides = new HashMap<>();
    private Set<AlgorithmTextListener> algorithmTextListeners = new CopyOnWriteArraySet<>();

    final CopyOnWriteArrayList<AlgorithmExecutionContext> contexts = new CopyOnWriteArrayList<>();

    static JavaAlgorithmEngine jae = new JavaAlgorithmEngine();
    static {
        registerScriptEngines();
        registerAlgorithmEngine("Java", jae);
        registerAlgorithmEngine("java", jae);
        registerAlgorithmEngine("java-expression", jae);
    }

    int maxErrCount;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        Spec libspec = new Spec();
        libspec.addOption("JavaScript", OptionType.LIST).withElementType(OptionType.STRING);
        libspec.addOption("python", OptionType.LIST).withElementType(OptionType.STRING);
        spec.addOption("libraries", OptionType.MAP).withSpec(libspec);
        spec.addOption("maxErrorsBeforeAutomaticDeactivation", OptionType.INTEGER)
                .withDescription("If an algorithm errors this number of times, it will be deactivated")
                .withDefault(10);
        return spec;
    }

    /**
     * Create a ScriptEngineManager for each of the script engines available in the jre.
     */
    private static void registerScriptEngines() {
        ScriptEngineManager sem = new ScriptEngineManager();
        for (ScriptEngineFactory sef : sem.getEngineFactories()) {

            // JDK11-14 are the last JDK versions to include a copy of Nashorn.
            // Disable this copy, so that only Nashorn from the classpath is used.
            // (both get detected by this loop with the same set of names).
            if (JDK_BUILTIN_NASHORN_ENGINE_NAME.equals(sef.getEngineName())) {
                continue;
            }

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
        this.parameterProcessorManager = processor.getParameterProcessorManager();

        this.parameterProcessorManager.addParameterProvider(this);
        this.parameterProcessorManager.subscribeAll(this);
        this.maxErrCount = config.getInt("maxErrorsBeforeAutomaticDeactivation", 10);

        mdb = processor.getMdb();
        timer = processor.getTimer();

        globalCtx = new AlgorithmExecutionContext("global", processor.getProcessorData(), maxErrCount);
        contexts.add(globalCtx);

        for (Algorithm algo : mdb.getAlgorithms()) {
            if (algo.getScope() == Algorithm.Scope.GLOBAL) {
                loadAlgorithm(algo, globalCtx);
            }
        }
    }

    private void loadAlgorithm(Algorithm algo, AlgorithmExecutionContext ctx) {
        for (OutputParameter oParam : algo.getOutputList()) {
            outParamIndex.add(oParam.getParameter());
        }
        // Eagerly activate the algorithm if no outputs (with lazy activation,
        // it would never trigger because there's nothing to subscribe to)
        if (algo.getOutputList().isEmpty() && !ctx.containsAlgorithm(algo.getQualifiedName())) {
            ActiveAlgorithm activeAlgo = activateAndInit(algo, ctx);
            List<OutputParameter> outList = activeAlgo.getOutputList();
            if (outList != null) {
                for (OutputParameter oParam : outList) {
                    outParamIndex.add(oParam.getParameter());
                }
            }
        }

        TriggerSetType tst = algo.getTriggerSet();
        if (tst == null) {
            eventProducer.sendWarning("No trigger set for algorithm '" + algo.getQualifiedName() + "'");
        } else {
            List<OnPeriodicRateTrigger> timedTriggers = tst.getOnPeriodicRateTriggers();
            if (!timedTriggers.isEmpty()) {
                final ActiveAlgorithm activeAlgo = activateAndInit(algo, ctx);
                if (activeAlgo != null) {
                    for (OnPeriodicRateTrigger trigger : timedTriggers) {
                        timer.scheduleAtFixedRate(() -> {
                            ProcessingData data = ProcessingData.createForTmProcessing(processor.getLastValueCache());
                            long t = processor.getCurrentTime();
                            List<ParameterValue> params = globalCtx.runAlgorithm(activeAlgo, t, t, data);
                            data.addTmParams(params);
                            parameterProcessorManager.process(data);
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

        for (Algorithm algo : mdb.getAlgorithms()) {
            for (OutputParameter oParam : algo.getOutputSet()) {
                if (oParam.getParameter() == paramDef) {
                    activateAndInit(algo, globalCtx);
                    // Account for multiple algorithms writing to same parameter
                    if (!requestedOutParams.contains(paramDef)) {
                        requestedOutParams.add(paramDef);
                    }
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
        AlgorithmExecutionContext ctx = new AlgorithmExecutionContext(name, processor.getProcessorData(), maxErrCount);
        contexts.add(ctx);
        return ctx;
    }

    public void removeContext(AlgorithmExecutionContext ctx) {
        contexts.remove(ctx);
    }

    /**
     * Activate an algorithm in a context.
     * <p>
     * If the algorithm cannot be activated (e.g. error compiling) returns null.
     */
    public ActiveAlgorithm activateAlgorithm(Algorithm algorithm, AlgorithmExecutionContext execCtx)
            throws AlgorithmException {
        ActiveAlgorithm activeAlgo = execCtx.getAlgorithm(algorithm.getQualifiedName());
        if (activeAlgo != null) {
            throw new IllegalStateException("Algorithm " + algorithm.getQualifiedName() + " already active");
        }

        AlgorithmExecutor executor;

        try {
            executor = makeExecutor(algorithm, execCtx);
        } catch (AlgorithmException e) {
            AlgorithmStatus algst = AlgorithmStatus.newBuilder()
                    .setErrorMessage("Failed to create executor"
                            + ((e.getMessage() == null) ? "" : ": " + e.getMessage()))
                    .setErrorTime(Timestamps.fromMillis(System.currentTimeMillis()))
                    .build();
            algorithmsInError.put(algorithm.getQualifiedName(), algst);
            throw e;
        }

        algorithmsInError.remove(algorithm.getQualifiedName());

        log.trace("Activating algorithm....{}", algorithm.getQualifiedName());
        activeAlgo = new ActiveAlgorithm(algorithm, execCtx, executor);

        subscribeRequiredParameters(activeAlgo);
        execCtx.addAlgorithm(activeAlgo);

        return activeAlgo;
    }

    private ActiveAlgorithm activateAndInit(Algorithm algorithm, AlgorithmExecutionContext execCtx) {
        ActiveAlgorithm activeAlgo = execCtx.getAlgorithm(algorithm.getQualifiedName());
        if (activeAlgo != null) {
            return activeAlgo;
        }
        try {
            activeAlgo = activateAlgorithm(algorithm, execCtx);
        } catch (AlgorithmException e) {
            return null;
        }

        // last value cache will contain the latest known values for all parameters
        // including the initialValue
        // TODO: should we also run the algorithm here???
        log.debug("Updating algorithm with initial values");
        activeAlgo.update(ProcessingData.createForTmProcessing(processor.getLastValueCache()));

        return activeAlgo;
    }

    private void subscribeRequiredParameters(ActiveAlgorithm activeAlgo) {
        enableBuffering(activeAlgo);

        ArrayList<Parameter> newItems = new ArrayList<>();
        for (Parameter param : getParametersOfInterest(activeAlgo)) {
            if (!requiredInParams.contains(param)) {
                requiredInParams.add(param);
                // Recursively activate other algorithms on which this algorithm depends
                if (canProvide(param)) {
                    for (Algorithm algo : mdb.getAlgorithms()) {
                        if (activeAlgo.getAlgorithm() != algo) {
                            for (OutputParameter oParam : algo.getOutputSet()) {
                                if (oParam.getParameter() == param) {
                                    activateAndInit(algo, globalCtx);
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
        }
        if (log.isTraceEnabled()) {
            log.trace("For algorithm {}, subscribing to the prm for {}", activeAlgo.getName(), newItems);
        }
        if (!newItems.isEmpty()) {
            parameterProcessorManager.subscribeToProviders(newItems);
        }
    }

    // if the input parameters require old values, make sure the parameter LastValueCache is configured for it
    private void enableBuffering(ActiveAlgorithm activeAlgo) {
        for (InputParameter inputPara : activeAlgo.getInputList()) {
            ParameterInstanceRef pref = inputPara.getParameterInstance();
            if (pref != null && pref.requireOldValues()) {
                if (pref.getInstance() < 0) {
                    processor.getLastValueCache().enableBuffering(pref.getParameter(), -pref.getInstance() + 1);
                } // else lastValueCache remembers anyway one value
            }
        }
    }

    AlgorithmExecutor makeExecutor(Algorithm algorithm, AlgorithmExecutionContext execCtx) throws AlgorithmException {
        AlgorithmExecutor executor;
        if (algorithm instanceof CustomAlgorithm) {
            CustomAlgorithm calg = (CustomAlgorithm) algorithm;
            AlgorithmExecutorFactory factory = getFactory(calg, execCtx);

            try {
                executor = factory.makeExecutor(calg, execCtx);
            } catch (AlgorithmException e) {
                log.warn("Failed to create algorithm executor", e);
                throw new AlgorithmException("Failed to create executor for algorithm "
                        + calg.getQualifiedName() + ": " + e, e);
            }
        } else if (algorithm instanceof MathAlgorithm) {
            executor = new MathAlgorithmExecutor(algorithm, execCtx, (MathAlgorithm) algorithm);
        } else {
            throw new AlgorithmException("Algorithms of type " + algorithm.getClass() + " not yet implemented");
        }

        return executor;
    }

    private AlgorithmExecutorFactory getFactory(CustomAlgorithm calg, AlgorithmExecutionContext execCtx) {
        String algLang = calg.getLanguage();
        if (algLang == null) {
            throw new AlgorithmException("no language specified for algorithm "
                    + "'" + calg.getQualifiedName() + "'");
        }
        AlgorithmExecutorFactory factory = factories.get(algLang);
        if (factory == null) {
            AlgorithmEngine eng = algorithmEngines.get(algLang);
            if (eng == null) {
                throw new AlgorithmException("no algorithm engine found for language '" + algLang + "'");
            }
            factory = eng.makeExecutorFactory(this, execCtx, algLang, config);
            factories.put(algLang, factory);
            for (String s : factory.getLanguages()) {
                factories.put(s, factory);
            }
        }
        return factory;
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
            for (Iterator<ActiveAlgorithm> it = Lists.reverse(globalCtx.executionOrder).iterator(); it.hasNext();) {
                ActiveAlgorithm activeAlgo = it.next();
                Algorithm algo = activeAlgo.getAlgorithm();
                boolean keep = false;

                // Keep if any other output parameters are still subscribed to
                for (OutputParameter oParameter : algo.getOutputSet()) {
                    if (requestedOutParams.contains(oParameter.getParameter())) {
                        keep = true;
                        break;
                    }
                    for (var otherAlgo : globalCtx.getActiveAlgorithms()) {
                        if (getParametersOfInterest(otherAlgo).contains(oParameter.getParameter())) {
                            keep = true;
                            break;
                        }
                    }
                }

                if (!keep) {
                    it.remove();
                    globalCtx.removeAlgorithm(algo);
                } else {
                    stillRequired.addAll(getParametersOfInterest(activeAlgo));
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

    /**
     * Called by PRM when new parameters are received.
     * 
     */
    @Override
    public void process(ProcessingData data) {
        for (AlgorithmExecutionContext ctx : contexts) {
            ctx.process(processor.getCurrentTime(), data);
        }
    }

    @Override
    public void setParameterProcessor(ParameterProcessor parameterRequestManager) {
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

    public void addAlgorithmTextListener(AlgorithmTextListener listener) {
        algorithmTextListeners.add(listener);
    }

    public void removeAlgorithmTextListener(AlgorithmTextListener listener) {
        algorithmTextListeners.remove(listener);
    }

    public void clearAlgorithmOverride(CustomAlgorithm calg) {
        CustomAlgorithm algOverr = algoOverrides.remove(calg);
        if (algOverr == null) {
            return;
        }
        globalCtx.removeAlgorithm(algOverr.getQualifiedName());
        activateAndInit(calg, globalCtx);
        notifyAlgorithmTextListeners(calg);
    }

    /**
     * Override the algorithm
     *
     * @param calg
     * @param text
     */
    public void overrideAlgorithm(CustomAlgorithm calg, String text) {
        CustomAlgorithm algOverr = algoOverrides.remove(calg);
        globalCtx.removeAlgorithm(calg.getQualifiedName());

        AlgorithmExecutorFactory factory = getFactory(calg, globalCtx);

        algOverr = calg.copy();
        algOverr.setAlgorithmText(text);
        algorithmsInError.remove(calg.getQualifiedName());

        try {
            AlgorithmExecutor executor = factory.makeExecutor(algOverr, globalCtx);
            ActiveAlgorithm activeAlgo = new ActiveAlgorithm(algOverr, globalCtx, executor);
            globalCtx.addAlgorithm(activeAlgo);

        } catch (AlgorithmException e) {
            log.warn("Failed to create algorithm executor", e);
            eventProducer.sendCritical("Failed to create executor for algorithm "
                    + algOverr.getQualifiedName() + ": " + e);
            AlgorithmStatus.Builder status = AlgorithmStatus.newBuilder()
                    .setErrorTime(Timestamps.fromMillis(System.currentTimeMillis()));
            if (e.getMessage() != null) {
                status.setErrorMessage(e.getMessage());
            }
            algorithmsInError.put(algOverr.getQualifiedName(), status.build());
            return;
        }

        algoOverrides.put(calg, algOverr);
        notifyAlgorithmTextListeners(calg);
    }

    public Collection<CustomAlgorithm> getAlgorithmOverrides() {
        return algoOverrides.values();
    }

    public CustomAlgorithm getAlgorithmOverride(Algorithm algo) {
        return algoOverrides.get(algo);
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
        return globalCtx.getTrace(algo.getQualifiedName());
    }

    public AlgorithmStatus getAlgorithmStatus(Algorithm algo) {
        AlgorithmStatus status = algorithmsInError.get(algo.getQualifiedName());
        return status == null ? globalCtx.getAlgorithmStatus(algo.getQualifiedName()) : status;
    }

    /**
     * Returns all the parameters that this algorithm want to receive updates on. This includes not only the input
     * parameters, but also any parameters that are part of the trigger set.
     */
    private static Set<Parameter> getParametersOfInterest(ActiveAlgorithm activeAlgo) {
        Stream<Parameter> inputParams = activeAlgo.getInputList().stream()
                .filter(ip -> ip.getParameterInstance() != null).map(ip -> ip.getParameterInstance()
                        .getParameter());
        if (activeAlgo.getTriggerSet() == null) {
            return inputParams.collect(Collectors.toSet());
        } else {
            Stream<Parameter> triggerParams = activeAlgo.getTriggerSet().getOnParameterUpdateTriggers().stream()
                    .map(t -> t.getParameter());
            return Stream.concat(triggerParams, inputParams).collect(Collectors.toSet());
        }
    }

    private void notifyAlgorithmTextListeners(CustomAlgorithm algorithm) {
        var override = getAlgorithmOverride(algorithm);
        var text = override != null ? override.getAlgorithmText() : algorithm.getAlgorithmText();
        algorithmTextListeners.forEach(l -> l.algorithmTextUpdated(algorithm, text));
    }
}
