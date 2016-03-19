package org.yamcs.algorithms;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.ConfigurationException;
import org.yamcs.DVParameterConsumer;
import org.yamcs.InvalidIdentification;
import org.yamcs.InvalidRequestIdentification;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterRequestManagerImpl;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.NamedDescriptionIndex;
import org.yamcs.xtce.OnPeriodicRateTrigger;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AbstractService;

/**
 * Manages the provision of requested parameters that require the execution of
 * one or more XTCE algorithms.
 * <p>
 * Upon initialization it will scan all algorithms, and schedule any that are
 * to be triggered periodically. OutputParameters of all algorithms will be
 * indexed, so that AlgorithmManager knows what parameters it can provide to the
 * ParameterRequestManager.
 * <p>
 * Algorithms and any needed algorithms that require earlier execution, will be
 * activated as soon as a request for one of its output parameters is
 * registered.
 * <p>
 * Algorithms default to JavaScript, but this can be overridden to other
 * scripting languages as long as they are included in the classpath. As a design
 * choice all algorithms within the same AlgorithmManager, share the same language.
 */
public class AlgorithmManager extends AbstractService implements ParameterProvider, DVParameterConsumer {
    private static final Logger log=LoggerFactory.getLogger(AlgorithmManager.class);
    static final String KEY_ALGO_NAME="algoName";

    XtceDb xtcedb;
    ScriptEngineManager scriptEngineManager;
    String yamcsInstance;

    //the id used for subscribing to the parameterManager
    int subscriptionId;

    // Index of all available out params
    NamedDescriptionIndex<Parameter> outParamIndex=new NamedDescriptionIndex<Parameter>();

    CopyOnWriteArrayList<AlgorithmEngine> executionOrder=new CopyOnWriteArrayList<AlgorithmEngine>();
    HashSet<Parameter> requiredInParams=new HashSet<Parameter>(); // required by this class
    ArrayList<Parameter> requestedOutParams=new ArrayList<Parameter>(); // requested by clients
    ParameterRequestManagerImpl parameterRequestManager;

    // For scheduling OnPeriodicRate algorithms
    ScheduledExecutorService timer = Executors.newScheduledThreadPool(1);;
    YProcessor yproc;
    AlgorithmExecutionContext globalCtx = new AlgorithmExecutionContext("global", null);

    public AlgorithmManager(String yamcsInstance) throws ConfigurationException {
        this(yamcsInstance, null);
    }

    @SuppressWarnings("unchecked")
    public AlgorithmManager(String yamcsInstance, Map<String, Object> config) throws ConfigurationException {
        this.yamcsInstance = yamcsInstance;

        Map<String,List<String>> libraries= null;
        if(config!=null) {
            if(config.containsKey("libraries")) {
                libraries=(Map<String,List<String>>)config.get("libraries");
            }
        }

        scriptEngineManager = new ScriptEngineManager();
        if(libraries!=null) {
            for(Map.Entry<String, List<String>> me: libraries.entrySet()) {
                String language = me.getKey();
                List<String> libraryNames = me.getValue();
                // Disposable ScriptEngine, just to eval libraries and put them in global scope
                ScriptEngine scriptEngine = scriptEngineManager.getEngineByName(language);
                if(scriptEngine==null) {
                    throw new ConfigurationException("Cannot get a script engine for language "+language);
                }
                try {
                    for(String lib:libraryNames) {
                        log.debug("Loading library "+lib);
                        File f=new File(lib);
                        if(!f.exists()) throw new ConfigurationException("Algorithm library file '"+f+"' does not exist");

                        scriptEngine.put(ScriptEngine.FILENAME, f.getPath()); // Improves error msgs
                        if (f.isFile()) {
                            scriptEngine.eval(new FileReader(f));
                        } else {
                            throw new ConfigurationException("Specified library is not a file: "+f);
                        }
                    }
                } catch(IOException e) { // Force exit. User should fix this before continuing
                    throw new ConfigurationException("Cannot read from library file", e);
                } catch(ScriptException e) { // Force exit. User should fix this before continuing
                    throw new ConfigurationException("Script error found in library file: "+e.getMessage(), e);
                }

                // Put engine bindings in shared global scope - we want the variables in the libraries to be global
                Bindings commonBindings=scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
                Set<String> existingBindings = new HashSet<String>(scriptEngineManager.getBindings().keySet());

                existingBindings.retainAll(commonBindings.keySet());
                if(!existingBindings.isEmpty()) {
                    throw new ConfigurationException("Overlapping definitions found while loading libraries for language "+language+": "+ existingBindings);
                }
                commonBindings.putAll(scriptEngineManager.getBindings());
                scriptEngineManager.setBindings(commonBindings);
            }
        }
    }

    @Override
    public void init(YProcessor yproc) {
        this.yproc = yproc;
        this.parameterRequestManager = yproc.getParameterRequestManager();
        xtcedb = yproc.getXtceDb();
        try {
            subscriptionId=parameterRequestManager.addRequest(new ArrayList<Parameter>(0), this);
        } catch (InvalidIdentification e) {
            log.error("InvalidIdentification while subscribing to the parameterRequestManager with an empty subscription list", e);
        }

        for(Algorithm algo : xtcedb.getAlgorithms()) {
            if(algo.getScope()==Algorithm.Scope.global) {
                loadAlgorithm(algo, globalCtx);
            }
        }
    }


    private void loadAlgorithm(Algorithm algo, AlgorithmExecutionContext ctx) {
        for(OutputParameter oParam:algo.getOutputSet()) {
            outParamIndex.add(oParam.getParameter());
        }
        // Eagerly activate the algorithm if no outputs (with lazy activation,
        // it would never trigger because there's nothing to subscribe to)
        if(algo.getOutputSet().isEmpty() && !ctx.containsAlgorithm(algo)) {
            activateAlgorithm(algo, ctx, null);
        }

        List<OnPeriodicRateTrigger> timedTriggers = algo.getTriggerSet().getOnPeriodicRateTriggers();
        if(!timedTriggers.isEmpty()) {
            // acts as a fixed-size pool
            activateAlgorithm(algo, ctx, null);
            final AlgorithmEngine engine = ctx.getEngine(algo);
            for(OnPeriodicRateTrigger trigger:timedTriggers) {
                timer.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        long t = yproc.getCurrentTime();
                        List<ParameterValue> params = engine.runAlgorithm(t, t);
                        parameterRequestManager.update(params);
                    }
                }, 1000, trigger.getFireRate(), TimeUnit.MILLISECONDS);
            }
        }
    }

    public int getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public void startProviding(Parameter paramDef) {
        if(requestedOutParams.contains(paramDef)) {
            return;
        }

        for(Algorithm algo:xtcedb.getAlgorithms()) {
            for(OutputParameter oParam:algo.getOutputSet()) {
                if(oParam.getParameter()==paramDef) {
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
     * @param name - name of the context 
     * @return the newly created context
     */
    public AlgorithmExecutionContext createContext(String name) {
        return new AlgorithmExecutionContext(name, globalCtx);
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
    public void activateAlgorithm(Algorithm algorithm, AlgorithmExecutionContext execCtx, AlgorithmExecListener listener) {
        AlgorithmEngine engine = execCtx.getEngine(algorithm);
        if(engine!=null) {
            log.trace("Already activated algorithm {} in context {}", algorithm.getQualifiedName(), execCtx.getName());
            if(listener!=null) {
                engine.addExecListener(listener);
            }
            return;
        }
        log.trace("Activating algorithm....{}", algorithm.getQualifiedName());

        ScriptEngine scriptEngine=scriptEngineManager.getEngineByName(algorithm.getLanguage());
        if(scriptEngine==null) throw new RuntimeException("Cannot created a script engine for language '"+algorithm.getLanguage()+"'");

        scriptEngine.put("Yamcs", new AlgorithmUtils(yproc, xtcedb, algorithm.getName()));

        engine=new AlgorithmEngine(algorithm, scriptEngine, execCtx);
        if(listener!=null) {
            engine.addExecListener(listener);
        }
    
        execCtx.addAlgorithm(algorithm, engine);
        try {
            ArrayList<Parameter> newItems=new ArrayList<Parameter>();
            for(Parameter param:engine.getRequiredParameters()) {
                if(!requiredInParams.contains(param)) {
                    requiredInParams.add(param);
                    // Recursively activate other algorithms on which this algorithm depends
                    if(canProvide(param)) {
                        for(Algorithm algo:xtcedb.getAlgorithms()) {
                            if(algorithm != algo) {
                                for(OutputParameter oParam:algo.getOutputSet()) {
                                    if(oParam.getParameter()==param) {
                                        activateAlgorithm(algo, execCtx, null);
                                    }
                                }
                            }
                        }
                    } else { // Don't ask items to PRM that we can provide ourselves or command verifier context parameters that PRM cannot provide 
                        if((param.getDataSource()!=DataSource.COMMAND) && param.getDataSource()!=DataSource.COMMAND_HISTORY) { 
                            newItems.add(param);
                        }
                    }
                }

                // Initialize a new Windowbuffer, or extend an existing one, if the algorithm requires going back in time
                int lookbackSize=engine.getLookbackSize(param);
                if(lookbackSize>0) {
                    execCtx.enableBuffer(param, lookbackSize);
                }
            }
            if(!newItems.isEmpty()) {
                parameterRequestManager.addItemsToRequest(subscriptionId, newItems);
            }
            executionOrder.add(engine); // Add at the back (dependent algorithms will come in front)
        } catch (InvalidIdentification e) {
            log.error(String.format("InvalidIdentification caught when subscribing to the items "
                    + "required for the algorithm %s\n\t The invalid items are: %s"
                    , engine.getAlgorithm().getName(), e.invalidParameters), e);
        } catch (InvalidRequestIdentification e) {
            log.error("InvalidRequestIdentification caught when subscribing to the items required for the algorithm "
                    + engine.getAlgorithm().getName(), e);
        }
    }
    
    public void deactivateAlgorithm(Algorithm algorithm, AlgorithmExecutionContext execCtx) {
        AlgorithmEngine engine = execCtx.remove(algorithm);
        if(engine!=null) {
            executionOrder.remove(engine);
        }
    }
    
    @Override
    public void startProvidingAll() {
        for(Parameter p:outParamIndex.getObjects()) {
            startProviding(p);
        }
    }

    @Override
    public void stopProviding(Parameter paramDef) {
        if(requestedOutParams.remove(paramDef)) {
            // Remove algorithm engine (and any that are no longer needed as a consequence)
            // We need to clean-up three more internal structures: requiredInParams, executionOrder and engineByAlgorithm
            HashSet<Parameter> stillRequired=new HashSet<Parameter>(); // parameters still required by any other algorithm
            for(Iterator<AlgorithmEngine> it=Lists.reverse(executionOrder).iterator();it.hasNext();) {
                AlgorithmEngine engine = it.next();
                Algorithm algo = engine.getAlgorithm();
                boolean doRemove=true;

                // Don't remove if any other output parameters are still subscribed to
                for(OutputParameter oParameter:algo.getOutputSet()) {
                    if(requestedOutParams.contains(oParameter.getParameter())) {
                        doRemove=false;
                        break;
                    }
                }

                if(!algo.canProvide(paramDef)) { // Clean-up unused engines
                    // For any of its outputs, check if it's still used by any algorithm
                    for(OutputParameter op:algo.getOutputSet()) {
                        if(requestedOutParams.contains(op.getParameter())) {
                            doRemove=false;
                            break;
                        }
                        for(Algorithm otherAlgo:globalCtx.getAlgorithms()) {
                            for(InputParameter ip:otherAlgo.getInputSet()) {
                                if(ip.getParameterInstance().getParameter()==op.getParameter()) {
                                    doRemove=false;
                                    break;
                                }
                            }
                            if(!doRemove) break;
                        }
                    }
                }

                if(doRemove) {
                    it.remove();
                    globalCtx.remove(algo);
                } else {
                    for(InputParameter p:algo.getInputSet()) {
                        stillRequired.add(p.getParameterInstance().getParameter());
                    }
                }
            }
            requiredInParams.retainAll(stillRequired);
        }
    }

    @Override
    public boolean canProvide(Parameter p) {
        return (outParamIndex.get(p.getQualifiedName())!=null);
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
        if(paraId.hasNamespace()) {
            p=outParamIndex.get(paraId.getNamespace(), paraId.getName());
        } else {
            p=outParamIndex.get(paraId.getName());
        }
        if(p!=null) {
            return p;
        } else {
            throw new InvalidIdentification();
        }
    }

    @Override
    public ArrayList<ParameterValue> updateParameters(int subscriptionId, ArrayList<ParameterValue> items) {
        return updateParameters(items, globalCtx);
    }
    
    /**
     * Update parameters in context and run the affected algorithms 
     * @param items
     * @param ctx
     * @return the parameters resulting from running the algorithms
     */
    public ArrayList<ParameterValue> updateParameters(List<ParameterValue> items, AlgorithmExecutionContext ctx) {
        ArrayList<ParameterValue> newItems=new ArrayList<ParameterValue>();
        
        ctx.updateHistoryWindows(items);
        long acqTime = yproc.getCurrentTime();
        long genTime = items.get(0).getGenerationTime();

        ArrayList<ParameterValue> allItems=new ArrayList<ParameterValue>(items);
        for(AlgorithmEngine engine:executionOrder) {
            if(ctx==globalCtx || engine.execCtx==ctx) {
                boolean shouldRun = engine.updateParameters(allItems);
                if(shouldRun) {
                    List<ParameterValue> r = engine.runAlgorithm(acqTime, genTime);
                    if(r!=null) {
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
    public void setParameterListener(ParameterRequestManager parameterRequestManager) {
        // do nothing, we're more interested in a ParameterRequestManager, which we're
        // getting from the constructor
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        if(timer!=null) {
            timer.shutdownNow();
        }
        notifyStopped();
    }
}
