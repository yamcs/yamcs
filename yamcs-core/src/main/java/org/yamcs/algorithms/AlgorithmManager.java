package org.yamcs.algorithms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.DVParameterConsumer;
import org.yamcs.InvalidIdentification;
import org.yamcs.InvalidRequestIdentification;
import org.yamcs.Processor;
import org.yamcs.ProcessorService;
import org.yamcs.api.EventProducer;
import org.yamcs.parameter.ParameterListener;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
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
import org.yamcs.xtce.TriggerSetType;
import org.yamcs.xtce.XtceDb;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AbstractService;

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
public class AlgorithmManager extends AbstractService
        implements ParameterProvider, DVParameterConsumer, ProcessorService {
    private static final Logger log = LoggerFactory.getLogger(AlgorithmManager.class);
    static final String KEY_ALGO_NAME = "algoName";

    XtceDb xtcedb;

    String yamcsInstance;

    // the id used for subscribing to the parameterManager
    int subscriptionId;

    // Index of all available out params
    NamedDescriptionIndex<Parameter> outParamIndex = new NamedDescriptionIndex<>();

    CopyOnWriteArrayList<AlgorithmExecutor> executionOrder = new CopyOnWriteArrayList<>();
    HashSet<Parameter> requiredInParams = new HashSet<>(); // required by this class
    ArrayList<Parameter> requestedOutParams = new ArrayList<>(); // requested by clients
    ParameterRequestManager parameterRequestManager;

    // For scheduling OnPeriodicRate algorithms
    ScheduledExecutorService timer = Executors.newScheduledThreadPool(1);
    Processor processor;
    AlgorithmExecutionContext globalCtx;

    Map<String, List<String>> libraries = null;
    EventProducer eventProducer;

    final static Map<String, AlgorithmEngine> algorithmEngines = new HashMap<>();

    final Map<String, AlgorithmExecutorFactory> factories = new HashMap<>();
    final Map<String, Object> config;
    static {
        registerScriptEngines();
        registerAlgorithmEngine("Java", new JavaAlgorithmEngine());
    }

    public AlgorithmManager(String yamcsInstance) throws ConfigurationException {
        this(yamcsInstance, (Map<String, Object>) null);
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

    @SuppressWarnings("unchecked")
    public AlgorithmManager(String yamcsInstance, Map<String, Object> config) throws ConfigurationException {
        this.yamcsInstance = yamcsInstance;
        this.config = config;
    }

    public static void registerAlgorithmEngine(String name, AlgorithmEngine eng) {
        algorithmEngines.put(name, eng);
    }

    @Override
    public void init(Processor yproc) {
        this.processor = yproc;
        this.eventProducer = yproc.getProcessorData().getEventProducer();
        this.parameterRequestManager = yproc.getParameterRequestManager();
        this.parameterRequestManager.addParameterProvider(this);
        xtcedb = yproc.getXtceDb();

        globalCtx = new AlgorithmExecutionContext("global", null, yproc.getProcessorData());
        subscriptionId = parameterRequestManager.addRequest(new ArrayList<Parameter>(0), this);

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
                final AlgorithmExecutor engine = ctx.getExecutor(algo);
                for (OnPeriodicRateTrigger trigger : timedTriggers) {
                    timer.scheduleAtFixedRate(() -> {
                        long t = processor.getCurrentTime();
                        List<ParameterValue> params = engine.runAlgorithm(t, t);
                        parameterRequestManager.update(params);
                    }, 1000, trigger.getFireRate(), TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    public int getSubscriptionId() {
        return subscriptionId;
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
        AlgorithmExecutor executor = execCtx.getExecutor(algorithm);
        if (executor != null) {
            log.trace("Already activated algorithm {} in context {}", algorithm.getQualifiedName(), execCtx.getName());
            if (listener != null) {
                executor.addExecListener(listener);
            }
            return;
        }
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
                factory = eng.makeExecutorFactory(this, algLang, config);
                factories.put(algLang, factory);
                for (String s : factory.getLanguages()) {
                    factories.put(s, factory);
                }
            }
            try {
                executor = factory.makeExecutor(calg, execCtx);
            } catch (AlgorithmException e) {
                log.warn("Failed to create algorithm executor", e);
                eventProducer
                        .sendCritical("Failed to create executor for algorithm " + calg.getQualifiedName() + ": " + e);
                return;
            }
        } else if (algorithm instanceof MathAlgorithm) {
            executor = new MathAlgorithmExecutor(algorithm, execCtx, (MathAlgorithm) algorithm);
        } else {
            throw new UnsupportedOperationException(
                    "Algorithms of type " + algorithm.getClass() + " not yet implemented");
        }
        if (listener != null) {
            executor.addExecListener(listener);
        }

        execCtx.addAlgorithm(algorithm, executor);
        try {
            ArrayList<Parameter> newItems = new ArrayList<>();
            for (Parameter param : executor.getRequiredParameters()) {
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

                // Initialize a new Windowbuffer, or extend an existing one, if the algorithm requires going back in
                // time
                int lookbackSize = executor.getLookbackSize(param);
                if (lookbackSize > 0) {
                    execCtx.enableBuffer(param, lookbackSize);
                }
            }
            if (!newItems.isEmpty()) {
                parameterRequestManager.addItemsToRequest(subscriptionId, newItems);
            }
            executionOrder.add(executor); // Add at the back (dependent algorithms will come in front)
        } catch (InvalidRequestIdentification e) {
            log.error("InvalidRequestIdentification caught when subscribing to the items required for the algorithm {}",
                    executor.getAlgorithm().getName(), e);
        }
    }

    public void deactivateAlgorithm(Algorithm algorithm, AlgorithmExecutionContext execCtx) {
        AlgorithmExecutor engine = execCtx.remove(algorithm);
        if (engine != null) {
            executionOrder.remove(engine);
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
            // Remove algorithm engine (and any that are no longer needed as a consequence)
            // We need to clean-up three more internal structures: requiredInParams, executionOrder and
            // engineByAlgorithm
            HashSet<Parameter> stillRequired = new HashSet<>(); // parameters still required by any other algorithm
            for (Iterator<AlgorithmExecutor> it = Lists.reverse(executionOrder).iterator(); it.hasNext();) {
                AlgorithmExecutor engine = it.next();
                Algorithm algo = engine.getAlgorithm();
                boolean doRemove = true;

                // Don't remove if any other output parameters are still subscribed to
                for (OutputParameter oParameter : algo.getOutputSet()) {
                    if (requestedOutParams.contains(oParameter.getParameter())) {
                        doRemove = false;
                        break;
                    }
                }

                if (!algo.canProvide(paramDef)) { // Clean-up unused engines
                    // For any of its outputs, check if it's still used by any algorithm
                    for (OutputParameter op : algo.getOutputSet()) {
                        if (requestedOutParams.contains(op.getParameter())) {
                            doRemove = false;
                            break;
                        }
                        for (Algorithm otherAlgo : globalCtx.getAlgorithms()) {
                            for (InputParameter ip : otherAlgo.getInputSet()) {
                                if (ip.getParameterInstance().getParameter() == op.getParameter()) {
                                    doRemove = false;
                                    break;
                                }
                            }
                            if (!doRemove) {
                                break;
                            }
                        }
                    }
                }

                if (doRemove) {
                    it.remove();
                    globalCtx.remove(algo);
                } else {
                    for (InputParameter p : algo.getInputSet()) {
                        stillRequired.add(p.getParameterInstance().getParameter());
                    }
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

    @Override
    public List<ParameterValue> updateParameters(int subscriptionId, List<ParameterValue> items) {
        return updateParameters(items, globalCtx);
    }

    /**
     * Update parameters in context and run the affected algorithms
     * 
     * @param items
     * @param ctx
     * @return the parameters resulting from running the algorithms
     */
    public List<ParameterValue> updateParameters(List<ParameterValue> items, AlgorithmExecutionContext ctx) {
        ArrayList<ParameterValue> newItems = new ArrayList<>();

        ctx.updateHistoryWindows(items);
        long acqTime = processor.getCurrentTime();
        long genTime = items.get(0).getGenerationTime();

        ArrayList<ParameterValue> allItems = new ArrayList<>(items);
        for (AlgorithmExecutor executor : executionOrder) {
            if (ctx == globalCtx || executor.getExecutionContext() == ctx) {
                boolean shouldRun = executor.updateParameters(allItems);
                if (shouldRun) {
                    List<ParameterValue> r = executor.runAlgorithm(acqTime, genTime);
                    if (r != null) {
                        allItems.addAll(r);
                        newItems.addAll(r);
                        ctx.updateHistoryWindows(r);
                    }
                }
            }
        }
        return newItems;
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
}
