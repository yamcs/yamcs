package org.yamcs.algorithms;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.Invocable;
import javax.script.ScriptException;

import org.codehaus.janino.SimpleCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.EventProducer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtceproc.DataEncodingDecoder;
import org.yamcs.xtceproc.ParameterTypeProcessor;
import org.yamcs.xtceproc.ParameterTypeUtils;

import com.google.protobuf.ByteString;

/**
 * Represents the execution context of one algorithm. An AlgorithmExecutor is reused upon each update of one or more of
 * its InputParameters.
 * <p>
 * This class will create and compile on-the-fly ValueBinding implementations for every unique combination of raw and
 * eng types. 
 */
public class ScriptAlgorithmExecutor extends AbstractAlgorithmExecutor {
    static final Logger log = LoggerFactory.getLogger(ScriptAlgorithmExecutor.class);

    final Invocable invocable;
    //stores both the function inputs and outputs
    //the position of the inputs corresponds to the position of AlgorithmDef input respectively output List   
    final Object[] functionArgs;
  
    final int numInputs;
    final int numOutputs;

    // Each ValueBinding class represent a unique raw/eng type combination (== key)
    private static Map<String, Class<ValueBinding>> valueBindingClasses = Collections
            .synchronizedMap(new HashMap<String, Class<ValueBinding>>());
    ParameterTypeProcessor parameterTypeProcessor;
    final String functionName;
    final EventProducer eventProducer;
    
    public ScriptAlgorithmExecutor(CustomAlgorithm algorithmDef, Invocable invocable,  String functionName, AlgorithmExecutionContext execCtx) {
        super(algorithmDef, execCtx);
        this.parameterTypeProcessor = new ParameterTypeProcessor(execCtx.getProcessorData());
        this.functionName = functionName;
        this.invocable = invocable;
        this.eventProducer = execCtx.getProcessorData().getEventProducer();
        
        numInputs = algorithmDef.getInputList().size();
        List<OutputParameter> outputList = algorithmDef.getOutputList();
        numOutputs = outputList.size();
        functionArgs = new Object[numInputs+numOutputs];

        // Set empty output bindings so that algorithms can write their attributes
        for (int k = 0; k<numOutputs; k++) {
            functionArgs[numInputs+k] = new OutputValueBinding();;
        }
    }

    @Override
    protected void updateInput(int position, InputParameter inputParameter, ParameterValue newValue) {
        if(log.isTraceEnabled()) {
            log.trace("Algo {} updating input {} with value {}", algorithmDef.getName(), ScriptAlgorithmExecutorFactory.getArgName(inputParameter), newValue);
        }
        ValueBinding valueBinding = (ValueBinding) functionArgs[position];
        // First time for an inputParameter, it will create a ValueBinding object.
        // Further calls will just update that object
        if (valueBinding == null) {
            valueBinding = toValueBinding(newValue);
            functionArgs[position] = valueBinding;
        }
        valueBinding.updateValue(newValue);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.yamcs.algorithms.AlgorithmExecutor#runAlgorithm(long, long)
     */
    @Override
    public synchronized List<ParameterValue> runAlgorithm(long acqTime, long genTime) {
        if(log.isTraceEnabled()) {
            log.trace(getRunningTraceString());
        }
        try {
            Object returnValue = invocable.invokeFunction(functionName, functionArgs);
            List<ParameterValue> outputValues = new ArrayList<>();
            List<OutputParameter> outputList = algorithmDef.getOutputList();
            for (int k = 0; k<numOutputs; k++) {
                OutputParameter outputParameter = outputList.get(k);
                OutputValueBinding res = (OutputValueBinding) functionArgs[numInputs+k];
                if (res.updated && res.value != null) {
                    ParameterValue pv = convertScriptOutputToParameterValue(outputParameter.getParameter(), res);
                    pv.setAcquisitionTime(acqTime);
                    pv.setGenerationTime(genTime);
                    outputValues.add(pv);
                }
            }
            propagateToListeners(returnValue, outputValues);
            
            return outputValues;
        } catch (ScriptException | NoSuchMethodException e) {
            log.warn("Error while executing algorithm: " + e.getMessage(), e);
            eventProducer.sendWarning("Error while executing algorithm: "+e.getMessage());
            return Collections.emptyList();
        }
    }

    
    private String getRunningTraceString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Running algorithm ").append(algorithmDef.getName())
        .append("( ");
        int pos = 0;
        for(InputParameter p: algorithmDef.getInputList()) {
            if(pos!=0) {
                sb.append(", ");
            }
            sb.append(ScriptAlgorithmExecutorFactory.getArgName(p)).append(": ")
            .append(String.valueOf(functionArgs[pos]));
            pos++;
        }
        sb.append(")");
        return sb.toString();
    }
    
    private ParameterValue convertScriptOutputToParameterValue(Parameter parameter, OutputValueBinding binding) {
        ParameterValue pval = new ParameterValue(parameter);
        ParameterType ptype = parameter.getParameterType();
        DataEncoding de = null; 

        if(ptype instanceof BaseDataType) {
            de = ((BaseDataType) ptype).getEncoding();
        }
        if (de != null) {
            Value v = DataEncodingDecoder.getRawValue(de, binding.value);
            if(v==null) {
                eventProducer.sendWarning(getAlgorithm().getName(), "Cannot convert raw value from algorithm output "
                        + "'"+binding.value+"' of type "+binding.value.getClass()+" into "+de);
                pval.setAcquisitionStatus(AcquisitionStatus.INVALID);
            } else {
                pval.setRawValue(v);
                parameterTypeProcessor.calibrate(pval);
            }
        } else {
            Value v = ParameterTypeUtils.getEngValue(ptype, binding.value);
            if(v==null) {
                eventProducer.sendWarning(getAlgorithm().getName(), "Cannot convert eng value from algorithm output "
                        + "'"+binding.value+"' of type "+binding.value.getClass()+" into "+ptype);
                pval.setAcquisitionStatus(AcquisitionStatus.INVALID);
            } else {
                pval.setEngineeringValue(v);
            }
        }
        return pval;
    }


  
    private static ValueBinding toValueBinding(ParameterValue pval) {
        try {
            Class<ValueBinding> clazz = getOrCreateValueBindingClass(pval);
            Constructor<ValueBinding> constructor = clazz.getConstructor();
            return constructor.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Could not instantiate object of custom class", e);
        }
    }

    private static Class<ValueBinding> getOrCreateValueBindingClass(ParameterValue pval) {
        String key;
        if (pval.getRawValue() == null) {
            key = "" + pval.getEngValue().getType().getNumber();
        } else {
            key = pval.getRawValue().getType().getNumber() + "_" + pval.getEngValue().getType().getNumber();
        }

        if (valueBindingClasses.containsKey(key)) {
            return valueBindingClasses.get(key);
        } else {
            String className = "ValueBinding" + key;
            StringBuilder source = new StringBuilder();
            source.append("package org.yamcs.algorithms;\n");
            source.append("import " + ByteString.class.getName() + ";\n");
            source.append("import " + ParameterValue.class.getName() + ";\n")
                    .append("public class " + className + " extends ValueBinding {\n");
            StringBuilder updateValueSource = new StringBuilder("  public void updateValue(ParameterValue v) {\n")
                    .append("    super.updateValue(v);\n");
            if (pval.getRawValue() != null) {
                updateValueSource.append(addValueType(source, pval.getRawValue(), true));
            }
            updateValueSource.append(addValueType(source, pval.getEngValue(), false));
            updateValueSource.append("  }\n");

            source.append(updateValueSource.toString());
            
            source.append("  public String toString() {\n")
                    .append("    return \"[");
            if (pval.getRawValue() != null) {
                source.append("r: \"+rawValue+\", ");
            }
            source.append("v: \"+value+\"]\";\n")
                    .append("  }\n"); 
            
            source.append("}");
            try {
                SimpleCompiler compiler = new SimpleCompiler();
                if (log.isTraceEnabled()) {
                    log.trace("Compiling this:{}\n", source);
                }

                compiler.cook(source.toString());
                @SuppressWarnings("unchecked")
                Class<ValueBinding> clazz = (Class<ValueBinding>) compiler.getClassLoader()
                        .loadClass("org.yamcs.algorithms." + className);
                valueBindingClasses.put(key, clazz);
                return clazz;
            } catch (Exception e) {
                throw new IllegalStateException("Could not compile custom class: "+source.toString(), e);
            }
        }
    }

    /**
     * Appends a raw or eng field with a getter of the given value
     * 
     * @return a matching code fragment to be included in the updateValue() method
     */
    private static String addValueType(StringBuilder source, Value v, boolean raw) {
        if (v.getType() == Type.BINARY) {
            if (raw) {
                source.append("  public ByteString rawValue;\n");
                return "    rawValue=v.getRawValue().getBinaryValue();\n";
            } else {
                source.append("  public ByteString value;\n");
                return "    value=v.getEngValue().getBinaryValue();\n";
            }
        } else if (v.getType() == Type.DOUBLE) {
            if (raw) {
                source.append("  public double rawValue;\n");
                return "    rawValue=v.getRawValue().getDoubleValue();\n";
            } else {
                source.append("  public double value;\n");
                return "    value=v.getEngValue().getDoubleValue();\n";
            }
        } else if (v.getType() == Type.FLOAT) {
            if (raw) {
                source.append("  public float rawValue;\n");
                return "    rawValue=v.getRawValue().getFloatValue();\n";
            } else {
                source.append("  public float value;\n");
                return "    value=v.getEngValue().getFloatValue();\n";
            }
        } else if (v.getType() == Type.UINT32) {
            if (raw) {
                source.append("  public int rawValue;\n");
                return "    rawValue=v.getRawValue().getUint32Value();\n";
            } else {
                source.append("  public int value;\n");
                return "    value=v.getEngValue().getUint32Value();\n";
            }
        } else if (v.getType() == Type.SINT32) {
            if (raw) {
                source.append("  public int rawValue;\n");
                return "    rawValue=v.getRawValue().getSint32Value();\n";
            } else {
                source.append("  public int value;\n");
                return "    value=v.getEngValue().getSint32Value();\n";
            }
        } else if (v.getType() == Type.UINT64) {
            if (raw) {
                source.append("  public long rawValue;\n");
                return "    rawValue=v.getRawValue().getUint64Value();\n";
            } else {
                source.append("  public long value;\n");
                return "    value=v.getEngValue().getUint64Value();\n";
            }
        } else if (v.getType() == Type.SINT64) {
            if (raw) {
                source.append("  public long rawValue;\n");
                return "    rawValue=v.getRawValue().getSint64Value();\n";
            } else {
                source.append("  public long value;\n");
                return "    value=v.getEngValue().getSint64Value();\n";
            }
        } else if (v.getType() == Type.STRING) {
            if (raw) {
                source.append("  public String rawValue;\n");
                return "    rawValue=v.getRawValue().getStringValue();\n";
            } else {
                source.append("  public String value;\n");
                return "    value=v.getEngValue().getStringValue();\n";
            }
        } else if (v.getType() == Type.BOOLEAN) {
            if (raw) {
                source.append("  public boolean rawValue;\n");
                return "    rawValue=v.getRawValue().getBooleanValue();\n";
            } else {
                source.append("  public boolean value;\n");
                return "    value=v.getEngValue().getBooleanValue();\n";
            }
        } else {
            throw new IllegalArgumentException("Unexpected value of type " + v.getType());
        }
    }

    @Override
    public String toString() {
        return algorithmDef.getName() +" executor "+invocable;
    }

}
