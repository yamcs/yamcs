package org.yamcs.algorithms;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Channel;
import org.yamcs.ConfigurationException;
import org.yamcs.DVParameterConsumer;
import org.yamcs.InvalidIdentification;
import org.yamcs.InvalidRequestIdentification;
import org.yamcs.ParameterListener;
import org.yamcs.ParameterProvider;
import org.yamcs.ParameterRequestManager;
import org.yamcs.ParameterValue;
import org.yamcs.ParameterValueWithId;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Algorithm.AutoActivateType;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.NamedDescriptionIndex;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.XtceDb;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AbstractService;

/**
 * Manages the provision of requested parameters that require the execution of
 * one or more XTCE algorithms.
 * <p>
 * Upon initialization it will scan all algorithms, and activate any that don't
 * require subscription. OutputParameters of all algorithms will be indexed, so
 * that AlgorithmManager knows what parameters it can provide to the
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
    private static final String DEFAULT_LANGUAGE="JavaScript"; // included by default in JDK
    static final String KEY_ALGO_NAME="algoName";

    XtceDb xtcedb;
    ScriptEngine scriptEngine;

    //the id used for subscribing to the parameterManager
    int subscriptionId;

    // Index of all available out params
    NamedDescriptionIndex<Parameter> outParamIndex=new NamedDescriptionIndex<Parameter>();

    HashMap<Algorithm,AlgorithmEngine> engineByAlgorithm=new HashMap<Algorithm,AlgorithmEngine>();
    ArrayList<Algorithm> executionOrder=new ArrayList<Algorithm>();
    HashSet<Parameter> requiredInParams=new HashSet<Parameter>(); // required by this class
    ArrayList<Parameter> requestedOutParams=new ArrayList<Parameter>(); // requested by clients
    ParameterRequestManager parameterRequestManager;

    // For storing a window of previous parameter instances
    HashMap<Parameter,WindowBuffer> buffersByParam = new HashMap<Parameter,WindowBuffer>();

    public AlgorithmManager(ParameterRequestManager parameterRequestManager, Channel chan) throws ConfigurationException {
        this(parameterRequestManager, chan, null);
    }

    public AlgorithmManager(ParameterRequestManager parameterRequestManager, Channel chan, Object args) throws ConfigurationException {
        this.xtcedb=chan.xtcedb;
        this.parameterRequestManager=parameterRequestManager;
        try {
            subscriptionId=parameterRequestManager.addRequest(new ArrayList<NamedObjectId>(0), this);
        } catch (InvalidIdentification e) {
            log.error("InvalidIdentification while subscribing to the parameterRequestManager with an empty subscription list", e);
        }

        String scriptLanguage=DEFAULT_LANGUAGE;
        List<String> libraries=new ArrayList<String>();
        if(args!=null) {
            Map<String,Object> m=(Map<String,Object>) args;
            if(m.containsKey("scriptLanguage")) {
                scriptLanguage=(String)m.get("scriptLanguage");
            }
            if(m.containsKey("libraries")) {
                libraries=(List<String>)m.get("libraries");
            }
        }

        scriptEngine = new ScriptEngineManager().getEngineByName(scriptLanguage);
        scriptEngine.put("Yamcs", new AlgorithmUtils(chan.getInstance(), xtcedb, scriptEngine));

        // Load custom algorithm libraries
        try {
            for(String lib:libraries) {
                File f=new File(lib);
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

        for(Algorithm algo : xtcedb.getAlgorithms()) {
            loadAlgorithm(algo);
        }
    }

    private void loadAlgorithm(Algorithm algo) {
        for(OutputParameter oParam:algo.getOutputSet()) {
            outParamIndex.add(oParam.getParameter());
        }
        // Activate the algorithm if requested
        if(algo.getAutoActivate()!=null && !engineByAlgorithm.containsKey(algo)) {
            switch (algo.getAutoActivate()) {
            case ALWAYS:
                activateAlgorithm(algo);
                break;
            case REALTIME_ONLY:
                if(!parameterRequestManager.channel.isReplay()) {
                    activateAlgorithm(algo);
                }
                break;
            case REPLAY_ONLY:
                if(parameterRequestManager.channel.isReplay()) {
                    activateAlgorithm(algo);
                }
                break;
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
                    activateAlgorithm(algo);
                    requestedOutParams.add(paramDef);
                    return; // There shouldn't be more ...
                }
            }
        }
    }

    private void activateAlgorithm(Algorithm algorithm) {
        if(engineByAlgorithm.containsKey(algorithm)) {
            log.trace("Already activated algorithm {}", algorithm.getQualifiedName());
            return;
        }
        log.trace("Activating algorithm....{}", algorithm.getQualifiedName());
        AlgorithmEngine engine=new AlgorithmEngine(algorithm, scriptEngine);
        engineByAlgorithm.put(algorithm, engine);
        try {
            ArrayList<NamedObjectId> newItems=new ArrayList<NamedObjectId>();
            for(Parameter param:engine.getRequiredParameters()) {
                NamedObjectId noid=NamedObjectId.newBuilder().setName(param.getQualifiedName()).build();
                if(!requiredInParams.contains(param)) {
                    requiredInParams.add(param);
                    // Recursively activate other algorithms on which this algorithm depends
                    if(canProvide(noid)) {
                        for(Algorithm algo:xtcedb.getAlgorithms()) {
                            if(algorithm != algo) {
                                for(OutputParameter oParam:algo.getOutputSet()) {
                                    if(oParam.getParameter()==param) {
                                        activateAlgorithm(algo);
                                    }
                                }
                            }
                        }
                    } else { // Don't ask items to PRM that we can provide ourselves
                        newItems.add(noid);
                    }
                }

                // Initialize a new Windowbuffer, or extend an existing one, if the algorithm requires going back in time
                int lookbackSize=engine.getLookbackSize(param);
                if(lookbackSize>0) {
                    if(buffersByParam.containsKey(param)) {
                        WindowBuffer buf = buffersByParam.get(param);
                        buf.expandIfNecessary(lookbackSize+1);
                    } else {
                        buffersByParam.put(param, new WindowBuffer(lookbackSize+1));
                    }
                }
            }
            if(!newItems.isEmpty()) {
                parameterRequestManager.addItemsToRequest(subscriptionId, newItems);
            }
            executionOrder.add(algorithm); // Add at the back (dependent algorithms will come in front)
        } catch (InvalidIdentification e) {
            log.error(String.format("InvalidIdentification caught when subscribing to the items "
                    + "required for the algorithm %s\n\t The invalid items are: %s"
                    , engine.getAlgorithm().getName(), e.invalidParameters), e);
        } catch (InvalidRequestIdentification e) {
            log.error("InvalidRequestIdentification caught when subscribing to the items required for the algorithm "
                    + engine.getAlgorithm().getName(), e);
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
	        for(Iterator<Algorithm> it=Lists.reverse(executionOrder).iterator();it.hasNext();) {
	            Algorithm algo=it.next();
                boolean doRemove=true;
                
                // Don't remove if always 'on' for this channel
                if(algo.getAutoActivate()==AutoActivateType.ALWAYS
                        || algo.getAutoActivate()==AutoActivateType.REALTIME_ONLY && !parameterRequestManager.channel.isReplay()
                        || algo.getAutoActivate()==AutoActivateType.REPLAY_ONLY && parameterRequestManager.channel.isReplay()) {
                    doRemove=false;
                }

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
	                    for(Algorithm otherAlgo:engineByAlgorithm.keySet()) {
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
                    engineByAlgorithm.remove(algo);
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
    public ArrayList<ParameterValue> updateParameters(int subscriptionId, ArrayList<ParameterValueWithId> items) {
        ArrayList<ParameterValue> pvals=new ArrayList<ParameterValue>();
        for(ParameterValueWithId pvwi:items) {
            pvals.add(pvwi.getParameterValue());
        }

        updateHistoryWindows(pvals);
        return doUpdateParameters(pvals);
    }

    private ArrayList<ParameterValue> doUpdateParameters(ArrayList<ParameterValue> items) {
        ArrayList<ParameterValue> newItems=new ArrayList<ParameterValue>();
        ArrayList<ParameterValue> allItems=new ArrayList<ParameterValue>(items);
        for(Algorithm algorithm:executionOrder) {
            AlgorithmEngine engine=engineByAlgorithm.get(algorithm);
            boolean updated=false; // Whether any of its input parameters is updated
            boolean skipRun=false;

            // Set algorithm arguments based on incoming values
            for(InputParameter inputParameter:algorithm.getInputSet()) {
                if(skipRun) break;
                ParameterInstanceRef pInstance=inputParameter.getParameterInstance();
                for(ParameterValue pval:allItems) {
                    if(pInstance.getParameter().equals(pval.def)) {
                        if(engine.getLookbackSize(pInstance.getParameter())==0) {
                            engine.updateInput(inputParameter, pval);
                        } else {
                            ParameterValue historicValue=buffersByParam.get(pval.def)
                                    .getHistoricValue(pInstance.getInstance());
                            if(historicValue!=null) {
                                engine.updateInput(inputParameter, historicValue);
                            } else { // Exclude algo as soon as one param is not available
                                skipRun=true;
                            }
                        }
                        updated=true;
                    }
                }
            }

            if(!skipRun && updated) {
                ArrayList<ParameterValue> r=runEngine(engine, items.get(0).getGenerationTime());
                newItems.addAll(r);
                allItems.addAll(r);
            }
        }
        return newItems;
    }

    private ArrayList<ParameterValue> runEngine(AlgorithmEngine engine, long genTime) {
        long acqTime=TimeEncoding.currentInstant();
        ArrayList<ParameterValue> r=new ArrayList<ParameterValue>();
        try {
            List<ParameterValue> pvals=engine.runAlgorithm();
            r.addAll(pvals);
        } catch (Exception e) {
            log.warn("Exception while updating algorithm "+engine.def, e);
        }

        for(ParameterValue pval:r) {
            pval.setAcquisitionTime(acqTime);
            pval.setGenerationTime(genTime);
        }

        updateHistoryWindows(r);
        return r;
    }

    private void updateHistoryWindows(ArrayList<ParameterValue> pvals) {
        for(ParameterValue pval:pvals) {
            if(buffersByParam.containsKey(pval.def)) {
                buffersByParam.get(pval.def).update(pval);
            }
        }
    }

    @Override
    public void setParameterListener(ParameterListener parameterRequestManager) {
        // do nothing,  we're more interested in a ParameterRequestManager, which we're
        // getting from the constructor
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }

    @Override
    public String getDetailedStatus() {
        return String.format("processing %d out of %d parameters",
                engineByAlgorithm.size(), xtcedb.getAlgorithms().size());
    }

}
