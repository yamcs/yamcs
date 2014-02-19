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
import java.util.Map.Entry;

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
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.NamedDescriptionIndex;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.XtceDb;

import com.google.common.util.concurrent.AbstractService;

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
	
	Map<Algorithm,AlgorithmEngine> engineByAlgorithm=new HashMap<Algorithm,AlgorithmEngine>();
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
        if("JavaScript".equals(algo.getLanguage())) {
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
        } else {
            log.warn(String.format("Algorithm %s: unsupported language \"%s\"", algo.getName(), algo.getLanguage()));
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
        AlgorithmEngine engine=engineByAlgorithm.get(algorithm);
        if(engine!=null) {
            log.trace("Already activated algorithm {}", algorithm.getQualifiedName());
            return;
        }
        log.trace("Activating algorithm....{}", algorithm.getQualifiedName());
        engine=new AlgorithmEngine(algorithm, scriptEngine);
        engineByAlgorithm.put(algorithm, engine);
        try {
            ArrayList<NamedObjectId> newItems=new ArrayList<NamedObjectId>();
            for(Parameter param:engine.getRequiredParameters()) {
                if(!requiredInParams.contains(param)) {
                    NamedObjectId noid=NamedObjectId.newBuilder().setName(param.getQualifiedName()).build();
                    newItems.add(noid);
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
                    }
                }
                
                // Initialize a new Windowbuffer, or extend an existing one, if the algorithm requires
                // going back in time
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
	
	//TODO 2.0 unsubscribe from the requested values
	@Override
    public void stopProviding(Parameter paramDef) {
		for(Iterator<Algorithm> it=engineByAlgorithm.keySet().iterator();it.hasNext(); ) {
		    Algorithm algo=it.next();
		    for(OutputParameter oParameter:algo.getOutputSet()) {
		        if(oParameter.getParameter().equals(paramDef)) {
		            it.remove();
		            return; // There shouldn't be more..
		        }
		    }
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
	    return doUpdateParameters(pvals, new HashSet<AlgorithmEngine>());
	}
	
	private ArrayList<ParameterValue> doUpdateParameters(ArrayList<ParameterValue> items, HashSet<AlgorithmEngine> hasRun) {
        // Set the correct arguments for every Algorithm involved
        HashSet<AlgorithmEngine> needsRun=new HashSet<AlgorithmEngine>();
        HashSet<AlgorithmEngine> invalid=new HashSet<AlgorithmEngine>();
        for(Entry<Algorithm,AlgorithmEngine> entry:engineByAlgorithm.entrySet()) {
            Algorithm def=entry.getKey();
            AlgorithmEngine engine=entry.getValue();

            // Prevent multiple runs when cascading
            if(hasRun.contains(engine)) {
                continue;
            }

            // Set algorithm arguments based on incoming values
            for(InputParameter inputParameter:def.getInputSet()) {
                ParameterInstanceRef pInstance=inputParameter.getParameterInstance();
                boolean matchFound=false;
                for(ParameterValue pval:items) {
                    if(pInstance.getParameter().equals(pval.def)) {
                        matchFound=true;
                        if(engine.getLookbackSize(pInstance.getParameter())==0) {
                            engine.updateInput(inputParameter, pval);
                            needsRun.add(engine);
                        } else {
                            ParameterValue historicValue=buffersByParam.get(pval.def)
                                            .getHistoricValue(pInstance.getInstance());
                            if(historicValue!=null) {
                                engine.updateInput(inputParameter, historicValue);
                                needsRun.add(engine);
                            } else {
                                invalid.add(engine); // Exclude algo as soon as one param is not available
                            }
                        }
                    }
                }
                // Exclude algorithms with missing arguments (for example, because it depends on another algorithm which needs to be run first)
                if(!matchFound) {
                    invalid.add(engine);
                    break; // No need to evaluate other input parameters
                }
            }
        }
        
        // Finally, run the algorithm
        if(needsRun.isEmpty()) {
            return null;
        } else {
            long acqTime=TimeEncoding.currentInstant();
            ArrayList<ParameterValue> r=new ArrayList<ParameterValue>();
            for(AlgorithmEngine engine:needsRun) {
                if(!invalid.contains(engine)) {
                    try {
                        List<ParameterValue> pvals=engine.runAlgorithm();
                        r.addAll(pvals);
                        hasRun.add(engine);
                    } catch (Exception e) {
                        log.warn("Exception while updating algorithm "+engine.def, e);
                    }
                }
            }
    
            for(ParameterValue pval:r) {
                pval.setAcquisitionTime(acqTime);
                pval.setGenerationTime(items.get(0).getGenerationTime());
            }
            
            // Cascade
            if(!r.isEmpty()) {
                updateHistoryWindows(r); 
                items.addAll(r);
                ArrayList<ParameterValue> cascadedPvals=doUpdateParameters(items, hasRun);
                if(cascadedPvals!=null) {
                    r.addAll(cascadedPvals);
                }
            }
            return r;
        }
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
