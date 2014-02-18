package org.yamcs.algorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ParameterValue;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;


public class AlgorithmEngine {
    static final Logger log=LoggerFactory.getLogger(AlgorithmEngine.class);
    
    ScriptEngine scriptEngine;
    Algorithm def;
    // Keep only unique arguments (for subscription purposes)
    protected Set<Parameter> requiredParameters=new HashSet<Parameter>();
    protected boolean updated=true;
	
	/**
	 * Constructs a derived value for the given parameter and argument ids
	 */
	public AlgorithmEngine(Algorithm algorithmDef, ScriptEngine scriptEngine) {
	    this.def=algorithmDef;
	    this.scriptEngine=scriptEngine;

	    for(InputParameter inputParameter:algorithmDef.getInputSet()) {
            requiredParameters.add(inputParameter.getParameterInstance().getParameter());
	    }
	}
	
	public Algorithm getAlgorithm() {
	    return def;
	}
	
	public Set<Parameter> getRequiredParameters() {
		return requiredParameters;
	}
	
	public int getLookbackSize(Parameter parameter) {
        // e.g. [ -3, -2, -1, 0 ]
        int min=0;
        for(InputParameter p:def.getInputSet()) {
            ParameterInstanceRef pInstance=p.getParameterInstance();
            if(pInstance.getParameter().equals(parameter) && pInstance.getInstance()<min) {
                min=p.getParameterInstance().getInstance();
            }
        }
        return -min;
	}
	
    public void updateInput(InputParameter inputParameter, ParameterValue newValue) {
        for(InputParameter input:def.getInputSet()) {
            if(input.equals(inputParameter)) {
                String scriptName=inputParameter.getInputName();
                if(scriptName==null) {
                    scriptName=inputParameter.getParameterInstance().getParameter().getName();
                }
                
                Value v = newValue.getEngValue();
                switch(v.getType()) {
                    case BINARY:
                        scriptEngine.put(scriptName, v.getBinaryValue().toByteArray());
                        break;
                    case DOUBLE:
                        scriptEngine.put(scriptName, v.getDoubleValue());
                        break;
                    case FLOAT:
                        scriptEngine.put(scriptName, v.getFloatValue());
                        break;
                    case SINT32:
                        scriptEngine.put(scriptName, v.getSint32Value());
                        break;
                    case SINT64:
                        scriptEngine.put(scriptName, v.getSint64Value());
                        break;
                    case STRING:
                        scriptEngine.put(scriptName, v.getStringValue());
                        break;
                    case UINT32:
                        scriptEngine.put(scriptName, v.getUint32Value()&0xFFFFFFFFL);
                        break;
                    case UINT64:
                        scriptEngine.put(scriptName, v.getUint64Value()&0xFFFFFFFFFFFFFFFFL);
                        break;
                    default:
                        log.warn("Ignoring update of unexpected value type {}", v.getType());
                }
            }
        }
    }
	
	/**
	 * Runs the associated algorithm with the latest InputParameters
	 * @return the outputted parameters, if any
	 */
	public List<ParameterValue> runAlgorithm() {
	    log.trace("Running algorithm '{}'",def.getName());
        scriptEngine.put(AlgorithmManager.KEY_ALGO_NAME, def.getName());
        scriptEngine.put(ScriptEngine.FILENAME, def.getQualifiedName()); // Improves error msg
	    try {
            scriptEngine.eval(def.getAlgorithmText());
        } catch (ScriptException e) {
            log.warn("Error while executing script: "+e.getMessage(), e);
            return Collections.emptyList();
        }
        
        List<ParameterValue> outputValues=new ArrayList<ParameterValue>();
        for(OutputParameter outputParameter:def.getOutputSet()) {
            String scriptName=outputParameter.getOutputName();
            if(scriptName==null) {
                scriptName=outputParameter.getParameter().getName();
            }
            Object res = scriptEngine.get(scriptName);
            if(res != null) {
                ParameterValue pval=new ParameterValue(outputParameter.getParameter(), false);
                if(res instanceof Double) {
                    Double dres=(Double)res;
                    if(dres.longValue()==dres.doubleValue()) {
                        pval.setUnsignedIntegerValue(dres.intValue());
                    } else {
                        pval.setDoubleValue(dres.doubleValue());
                    }
                } else if(res instanceof String) {
                    pval.setStringValue((String) res);
                } else if(res instanceof Boolean) {
                    pval.setBinaryValue((((Boolean)res).booleanValue() ? "YES" : "NO").getBytes());
                } else {
                    pval.setBinaryValue(res.toString().getBytes());
                }
                outputValues.add(pval);
            }
        }
        return outputValues; 
	}

	public boolean isUpdated() {
		return updated;
	}
	
	@Override
	public String toString() {
	    return def.getName();
	}
}
