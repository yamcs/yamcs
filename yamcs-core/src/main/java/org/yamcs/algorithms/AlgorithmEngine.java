package org.yamcs.algorithms;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.codehaus.janino.SimpleCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ParameterValue;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanDataEncoding;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding.Encoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtceproc.ParameterTypeProcessor;

import com.google.protobuf.ByteString;

/**
 * Represents the execution context of one algorithm. An AlgorithmEngine is reused
 * upon each update of one or more of its InputParameters.
 * <p>
 * This class will create and compile on-the-fly ValueBinding implementations for every
 * unique combination of raw and eng types. The reason for this is to get the mapping
 * correct from Java to JavaScript. Rhino (the default JavaScript engine for JDK<=7)
 * will map java Float, Integer, etc towards javascript Object, instead of Number. As
 * a result, in javascript, using the plus operator on two supposed numbers would do a
 * string concatenation instead of an addition.
 * <p>
 * Rather than changing the Rhino configuration (which would require drastic tampering
 * of the maven-compiler-plugin in order to lift Sun's Access Restrictions on these
 * internal classes), we generate classes with primitive raw/eng values when needed.  
 */
public class AlgorithmEngine {
    static final Logger log=LoggerFactory.getLogger(AlgorithmEngine.class);
    
    ScriptEngine scriptEngine;
    Bindings bindings; // Bindings applicable for this algorithm only 
    Algorithm def;
    
    // Keep only unique arguments (for subscription purposes)
    private Set<Parameter> requiredParameters=new HashSet<Parameter>();
    // Keeps one ValueBinding instance per InputParameter, recycled on each algorithm run
    private Map<InputParameter,ValueBinding> bindingsByInput=new HashMap<InputParameter,ValueBinding>();
    // Keeps one OutputValueBinding instance per OutputParameter, recycled on each algorithm run
    private Map<OutputParameter,OutputValueBinding> bindingsByOutput=new HashMap<OutputParameter,OutputValueBinding>();
    // Each ValueBinding class represent a unique raw/eng type combination (== key)
    private static Map<String, Class<ValueBinding>> valueBindingClasses=Collections.synchronizedMap(new HashMap<String,Class<ValueBinding>>());
    
    protected boolean updated=true;
	
	public AlgorithmEngine(Algorithm algorithmDef, ScriptEngine scriptEngine) {
	    this.def=algorithmDef;
	    this.scriptEngine=scriptEngine;
	    bindings=scriptEngine.createBindings();
	    bindings.putAll(scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE));

	    for(InputParameter inputParameter:algorithmDef.getInputSet()) {
            requiredParameters.add(inputParameter.getParameterInstance().getParameter());
	    }
	    
	    // Set empty output bindings so that algorithms can write their attributes
	    for(OutputParameter outputParameter:algorithmDef.getOutputSet()) {
	        OutputValueBinding valueBinding=new OutputValueBinding();
	        bindingsByOutput.put(outputParameter, valueBinding);
	        String scriptName=outputParameter.getOutputName();
	        if(scriptName==null) {
	            scriptName=outputParameter.getParameter().getName();
	        }
	        bindings.put(scriptName, valueBinding);
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
	    // First time for an inputParameter, it will register a ValueBinding object with the engine.
	    // Further calls will just update that object
        if(!bindingsByInput.containsKey(inputParameter)) {
            ValueBinding valueBinding = toValueBinding(newValue);
            bindingsByInput.put(inputParameter, valueBinding);
            for(InputParameter input:def.getInputSet()) {
                if(input.equals(inputParameter)) {
                    String scriptName=inputParameter.getInputName();
                    if(scriptName==null) {
                        scriptName=inputParameter.getParameterInstance().getParameter().getName();
                    }
                    bindings.put(scriptName, valueBinding);
                }
            }
        }
        bindingsByInput.get(inputParameter).updateValue(newValue);
    }
	
	/**
	 * Runs the associated algorithm with the latest InputParameters
	 * @return the outputted parameters, if any
	 */
	public List<ParameterValue> runAlgorithm() {
	    log.trace("Running algorithm '{}'",def.getName());
        scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).put(AlgorithmManager.KEY_ALGO_NAME, def.getName());
        bindings.put(ScriptEngine.FILENAME, def.getQualifiedName()); // Improves error msg
	    try {
            scriptEngine.eval(def.getAlgorithmText(), bindings);
        } catch (ScriptException e) {
            log.warn("Error while executing algorithm: "+e.getMessage(), e);
            return Collections.emptyList();
        }
        
        List<ParameterValue> outputValues=new ArrayList<ParameterValue>();
        for(OutputParameter outputParameter:def.getOutputSet()) {
            String scriptName=outputParameter.getOutputName();
            if(scriptName==null) {
                scriptName=outputParameter.getParameter().getName();
            }
            if(bindings.get(scriptName) instanceof OutputValueBinding) {
                OutputValueBinding res = (OutputValueBinding) bindings.get(scriptName);
                if(res != null) {
                    outputValues.add(convertScriptOutputToParameterValue(outputParameter.getParameter(), res));
                }
            } else {
                log.warn("Error while executing algorithm {}. Wrong type of output parameter. Ensure you assign to '{}.value' and not directly to '{}'",
                        def.getQualifiedName(), scriptName, scriptName);
            }
            
        }
        return outputValues; 
	}
	
	private ParameterValue convertScriptOutputToParameterValue(Parameter parameter, OutputValueBinding binding) {
        ParameterValue pval=new ParameterValue(parameter, true);
        ParameterType ptype=parameter.getParameterType();
        if(ptype instanceof EnumeratedParameterType) {
            setRawValue(((EnumeratedParameterType) ptype).getEncoding(), pval, binding.value);
        } else if(ptype instanceof IntegerParameterType) {
            setRawValue(((IntegerParameterType) ptype).getEncoding(), pval, binding.value);
        } else if(ptype instanceof FloatParameterType) {
            setRawValue(((FloatParameterType) ptype).getEncoding(), pval, binding.value);
        } else if(ptype instanceof BinaryParameterType) {
            setRawValue(((BinaryParameterType) ptype).getEncoding(), pval, binding.value);
        } else if(ptype instanceof StringParameterType) {
            setRawValue(((StringParameterType) ptype).getEncoding(), pval, binding.value);
        } else if(ptype instanceof BooleanParameterType) {
            setRawValue(((BooleanParameterType) ptype).getEncoding(), pval, binding.value);
        } else {
            throw new IllegalArgumentException("Unsupported parameter type "+ptype);
        }
        
        ParameterTypeProcessor.calibrate(pval);
        return pval;
	}
	
	private void setRawValue(DataEncoding de, ParameterValue pval, Object outputValue) {
        if(de instanceof IntegerDataEncoding) {
            setRawIntegerValue((IntegerDataEncoding) de, pval, outputValue);
        } else if(de instanceof FloatDataEncoding) {
            setRawFloatValue((FloatDataEncoding) de, pval, outputValue);
        } else if(de instanceof StringDataEncoding) {
            pval.setRawValue(outputValue.toString());
        } else if(de instanceof BooleanDataEncoding) {
            if(outputValue instanceof Boolean) {
                pval.setRawValue((Boolean)outputValue);
            } else {
                log.error("Could not set boolean value of parameter "+pval.getParameter().getName()+". Algorithm returned wrong type: "+outputValue.getClass());
            }
        } else {
            log.error("DataEncoding "+de+" not implemented as a raw return type for algorithms");
            throw new RuntimeException("DataEncoding "+de+" not implemented as a raw return type for algorithms");
        }
	}
	
	private void setRawIntegerValue(IntegerDataEncoding ide, ParameterValue pv, Object outputValue) {
	    long longValue;
	    if(outputValue instanceof Number) {
	        longValue=((Number)outputValue).longValue();
	    } else {
	        log.warn("Unexpected script return type for "+pv.getParameter().getName()+". Was expecting a number, but got: "+outputValue.getClass());
	        return; // TODO make exc, and catch to send to ev
	    }
        if(ide.getSizeInBits() <= 32) {
            if(ide.getEncoding() == Encoding.unsigned) {
                pv.setRawUnsignedInteger((int)longValue);
            } else {
                pv.setRawSignedInteger((int)longValue);
            }
        } else {
            if(ide.getEncoding() == Encoding.unsigned) {
                pv.setRawUnsignedLong(longValue);
            } else {
                pv.setRawSignedLong(longValue);
            }
        }
	}
	
	private void setRawFloatValue(FloatDataEncoding fde, ParameterValue pv, Object outputValue) {
	    double doubleValue;
	    if(outputValue instanceof Number) {
	        doubleValue=((Number)outputValue).doubleValue();
	    } else {
	        log.warn("Unexpected script return type for "+pv.getParameter().getName()+". Was expecting a number, but got: "+outputValue.getClass());
	        return; // TODO make exc, and catch to send to ev
	    }
        if(fde.getSizeInBits() <= 32) {
            pv.setRawValue((float) doubleValue);
        } else {
            pv.setRawValue(doubleValue);
        }
	}
	
    public boolean isUpdated() {
        return updated;
    }
    
    @Override
    public String toString() {
        return def.getName();
    }
	
	private static ValueBinding toValueBinding(ParameterValue pval) {
	    try {
	        Class<ValueBinding> clazz=getOrCreateValueBindingClass(pval);
	        Constructor<ValueBinding> constructor=clazz.getConstructor();
	        return constructor.newInstance();
	    } catch (Exception e) {
	        throw new IllegalStateException("Could not instantiate object of custom class", e);
	    }
	}
	
	private static Class<ValueBinding> getOrCreateValueBindingClass(ParameterValue pval) {
	    String key;
	    if(pval.getRawValue()==null) {
	        key=""+pval.getEngValue().getType().getNumber();
	    } else {
	        key=pval.getRawValue().getType().getNumber()+"_"+pval.getEngValue().getType().getNumber();
	    }
	    
	    if(valueBindingClasses.containsKey(key)) {
	        return valueBindingClasses.get(key);
	    } else {
	        String className="ValueBinding"+key;
	        StringBuilder source=new StringBuilder();
	        source.append("package org.yamcs.algorithms;\n");
	        source.append("import "+ByteString.class.getName()+";\n");
	        source.append("import "+ParameterValue.class.getName()+";\n")
	            .append("public class " + className + " extends ValueBinding {\n");
	        StringBuilder updateValueSource=new StringBuilder("  public void updateValue(ParameterValue v) {\n")
	            .append("    super.updateValue(v);\n");
	        if(pval.getRawValue() != null) {
	            updateValueSource.append(addValueType(source, pval.getRawValue(), true));
	        }
	        updateValueSource.append(addValueType(source, pval.getEngValue(), false));
	        updateValueSource.append("  }\n");
	        
	        source.append(updateValueSource.toString());
	        source.append("}");
	        
	        try {
	            SimpleCompiler compiler=new SimpleCompiler();
	            if(log.isTraceEnabled()) {
	                log.trace("Compiling this:\n"+source.toString());
	            }
	            compiler.cook(source.toString());
	            @SuppressWarnings("unchecked")
                Class<ValueBinding> clazz=(Class<ValueBinding>) compiler.getClassLoader().loadClass("org.yamcs.algorithms."+className);
	            valueBindingClasses.put(key, clazz);
	            return clazz;
	        } catch(Exception e) {
	            throw new IllegalStateException("Could not compile custom class", e);
	        }
	    }
	}
	
    /**
     * Appends a raw or eng field with a getter of the given value
     * @return a matching code fragment to be included in the updateValue() method
     */
	private static String addValueType(StringBuilder source, Value v, boolean raw) {
        if(v.getType() == Type.BINARY) {
            if(raw) {
                source.append("  public ByteString rawValue;\n");
                return "    rawValue=v.getRawValue().getBinaryValue();\n";
            } else {
                source.append("  public ByteString value;\n");
                return "    value=v.getEngValue().getBinaryValue();\n";
            }
        } else if(v.getType() == Type.DOUBLE) {
            if(raw) {
                source.append("  public double rawValue;\n");
                return "    rawValue=v.getRawValue().getDoubleValue();\n";
            } else {
                source.append("  public double value;\n");
                return "    value=v.getEngValue().getDoubleValue();\n";
            }
        } else if(v.getType() == Type.FLOAT) {
            if(raw) {
                source.append("  public float rawValue;\n");
                return "    rawValue=v.getRawValue().getFloatValue();\n";
            } else {
                source.append("  public float value;\n");
                return "    value=v.getEngValue().getFloatValue();\n";
            }
        } else if(v.getType() == Type.UINT32) {
            if(raw) {
                source.append("  public int rawValue;\n");
                return "    rawValue=v.getRawValue().getUint32Value();\n";
            } else {
                source.append("  public int value;\n");
                return "    value=v.getEngValue().getUint32Value();\n";
            }
        } else if(v.getType() == Type.SINT32) {
            if(raw) {
                source.append("  public int rawValue;\n");
                return "    rawValue=v.getRawValue().getSint32Value();\n";
            } else {
                source.append("  public int value;\n");
                return "    value=v.getEngValue().getSint32Value();\n";
            }
        } else if(v.getType() == Type.UINT64) {
            if(raw) {
                source.append("  public long rawValue;\n");
                return "    rawValue=v.getRawValue().getUint64Value();\n";
            } else {
                source.append("  public long value;\n");
                return "    value=v.getEngValue().getUint64Value();\n";
            }
        } else if(v.getType() == Type.SINT64) {
            if(raw) {
                source.append("  public long rawValue;\n");
                return "    rawValue=v.getRawValue().getSint64Value();\n";
            } else {
                source.append("  public long value;\n");
                return "    value=v.getEngValue().getSint64Value();\n";
            }
        } else if(v.getType() == Type.STRING) {
            if(raw) {
                source.append("  public String rawValue;\n");
                return "    rawValue=v.getRawValue().getStringValue();\n";
            } else {
                source.append("  public String value;\n");
                return "    value=v.getEngValue().getStringValue();\n";
            }
        } else if(v.getType() == Type.BOOLEAN) {
            if(raw) {
                source.append("  public boolean rawValue;\n");
                return "    rawValue=v.getRawValue().getBooleanValue();\n";
            } else {
                source.append("  public boolean value;\n");
                return "    value=v.getEngValue().getBooleanValue();\n";
            }
        } else {
            throw new IllegalArgumentException("Unexpected value of type "+v.getType());
        }
    }
}
