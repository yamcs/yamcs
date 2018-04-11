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
import org.yamcs.parameter.SInt32Value;
import org.yamcs.parameter.SInt64Value;
import org.yamcs.parameter.UInt32Value;
import org.yamcs.parameter.UInt64Value;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.BooleanDataEncoding;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding.Encoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtceproc.ParameterTypeProcessor;

import com.google.protobuf.ByteString;

/**
 * Represents the execution context of one algorithm. An AlgorithmEngine is reused upon each update of one or more of
 * its InputParameters.
 * <p>
 * This class will create and compile on-the-fly ValueBinding implementations for every unique combination of raw and
 * eng types. The reason for this is to get the mapping correct from Java to JavaScript. Rhino (the default JavaScript
 * engine for JDK &le; 7) will map java Float, Integer, etc towards javascript Object, instead of Number. As a result,
 * in javascript, using the plus operator on two supposed numbers would do a string concatenation instead of an
 * addition.
 * <p>
 * Rather than changing the Rhino configuration (which would require drastic tampering of the maven-compiler-plugin in
 * order to lift Sun's Access Restrictions on these internal classes), we generate classes with primitive raw/eng values
 * when needed.
 */
public class ScriptAlgorithmExecutor extends AbstractAlgorithmExecutor {
    static final Logger log = LoggerFactory.getLogger(ScriptAlgorithmExecutor.class);

    final Invocable invocable;
    final Object[] functionArgs;
    final Map<Object, Integer> argumentPosition = new HashMap<>();

    // Each ValueBinding class represent a unique raw/eng type combination (== key)
    private static Map<String, Class<ValueBinding>> valueBindingClasses = Collections
            .synchronizedMap(new HashMap<String, Class<ValueBinding>>());
    ParameterTypeProcessor parameterTypeProcessor;
    final String functionName;
    final EventProducer eventProducer;
    
    public ScriptAlgorithmExecutor(CustomAlgorithm algorithmDef, Invocable invocable,  String functionName, AlgorithmExecutionContext execCtx, EventProducer eventProducer) {
        super(algorithmDef, execCtx);
        this.parameterTypeProcessor = new ParameterTypeProcessor(execCtx.getProcessorData());
        this.functionName = functionName;
        this.invocable = invocable;
        this.eventProducer = eventProducer;
        
        functionArgs = new Object[algorithmDef.getInputSet().size() + algorithmDef.getOutputSet().size()];
        int position = 0;
        for (InputParameter inputParameter : algorithmDef.getInputSet()) {
            argumentPosition.put(inputParameter, position++);
        }
        // Set empty output bindings so that algorithms can write their attributes
        for (OutputParameter outputParameter : algorithmDef.getOutputSet()) {
            OutputValueBinding valueBinding = new OutputValueBinding();
            functionArgs[position] = valueBinding;
            argumentPosition.put(outputParameter, position++);
        }
    }

    @Override
    protected void updateInput(InputParameter inputParameter, ParameterValue newValue) {
        if(log.isTraceEnabled()) {
            log.trace("Algo {} updating input {} with value {}", algorithmDef.getName(), ScriptAlgorithmManager.getArgName(inputParameter), newValue);
        }
        int position = argumentPosition.get(inputParameter);
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
            StringBuilder sb = new StringBuilder();
            sb.append("Running algorithm ").append(algorithmDef.getName())
            .append("( ");
            int pos = 0;
            for(InputParameter p: algorithmDef.getInputList()) {
                if(pos!=0) {
                    sb.append(", ");
                }
                sb.append(ScriptAlgorithmManager.getArgName(p)).append(": ")
                .append(String.valueOf(functionArgs[pos]));
                pos++;
            }
            sb.append(")");

            log.trace(sb.toString());
        }
        try {
            Object returnValue = invocable.invokeFunction(functionName, functionArgs);
            List<ParameterValue> outputValues = new ArrayList<>();
            for (OutputParameter outputParameter : algorithmDef.getOutputSet()) {
                int pos = argumentPosition.get(outputParameter);
                OutputValueBinding res = (OutputValueBinding) functionArgs[pos];
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
            eventProducer.sendWarning(EventProducer.TYPE_ALGO_RUN, "Error while executing algorithm: "+e.getMessage());
            return Collections.emptyList();
        }
    }

    private ParameterValue convertScriptOutputToParameterValue(Parameter parameter, OutputValueBinding binding) {
        ParameterValue pval = new ParameterValue(parameter);
        ParameterType ptype = parameter.getParameterType();

        DataEncoding de = ptype.getEncoding();
        if (de != null) {
            setRawValue(ptype.getEncoding(), pval, binding.value);
            parameterTypeProcessor.calibrate(pval);
        } else {
            setEngValue(ptype, pval, binding.value);
        }
        return pval;
    }

    private void setEngValue(ParameterType ptype, ParameterValue pval, Object outputValue) {
        if (ptype instanceof IntegerParameterType) {
            setEngIntegerValue((IntegerParameterType) ptype, pval, outputValue);
        } else if (ptype instanceof FloatParameterType) {
            setEngFloatValue((FloatParameterType) ptype, pval, outputValue);
        } else if (ptype instanceof StringParameterType) {
            if (outputValue instanceof String) {
                pval.setStringValue((String) outputValue);
            } else {
                log.error("Could not set string value of parameter {}. Algorithm returned wrong type: {}",
                        pval.getParameter().getName(), outputValue.getClass());
            }
        } else if (ptype instanceof BooleanParameterType) {
            if (outputValue instanceof Boolean) {
                pval.setBooleanValue((Boolean) outputValue);
            } else {
                log.error("Could not set boolean value of parameter {}. Algorithm returned wrong type: {}",
                        pval.getParameter().getName(), outputValue.getClass());
            }
        } else {
            log.error("ParameterType {} not implemented as a return type for algorithms", ptype);
            pval.setAcquisitionStatus(AcquisitionStatus.INVALID);
        }
    }

    private void setRawValue(DataEncoding de, ParameterValue pval, Object outputValue) {
        if (de instanceof IntegerDataEncoding) {
            setRawIntegerValue((IntegerDataEncoding) de, pval, outputValue);
        } else if (de instanceof FloatDataEncoding) {
            setRawFloatValue((FloatDataEncoding) de, pval, outputValue);
        } else if (de instanceof StringDataEncoding) {
            pval.setRawValue(outputValue.toString());
        } else if (de instanceof BooleanDataEncoding) {
            if (outputValue instanceof Boolean) {
                pval.setRawValue((Boolean) outputValue);
            } else {
                log.error("Could not set boolean value of parameter {}. Algorithm returned wrong type: {}",
                        pval.getParameter().getName(), outputValue.getClass());
            }
        } else {
            log.error("DataEncoding {} not implemented as a raw return type for algorithms", de);
            throw new IllegalArgumentException(
                    "DataEncoding " + de + " not implemented as a raw return type for algorithms");
        }
    }

    private void setRawIntegerValue(IntegerDataEncoding ide, ParameterValue pv, Object outputValue) {
        long longValue;
        if (outputValue instanceof Number) {
            longValue = ((Number) outputValue).longValue();
        } else {
            pv.setAcquisitionStatus(AcquisitionStatus.INVALID);
            log.warn("Unexpected script return type for " + pv.getParameter().getName()
                    + ". Was expecting a number, but got: " + outputValue.getClass());
            return;
        }
        if (ide.getSizeInBits() <= 32) {
            if (ide.getEncoding() == Encoding.UNSIGNED) {
                pv.setRawUnsignedInteger((int) longValue);
            } else {
                pv.setRawSignedInteger((int) longValue);
            }
        } else {
            if (ide.getEncoding() == Encoding.UNSIGNED) {
                pv.setRawUnsignedLong(longValue);
            } else {
                pv.setRawSignedLong(longValue);
            }
        }
    }

    private void setEngIntegerValue(IntegerParameterType ptype, ParameterValue pv, Object outputValue) {
        long longValue;
        if (outputValue instanceof Number) {
            longValue = ((Number) outputValue).longValue();
        } else {
            pv.setAcquisitionStatus(AcquisitionStatus.INVALID);
            log.warn("Unexpected script return type for " + pv.getParameter().getName()
                    + ". Was expecting a number, but got: " + outputValue.getClass());
            return;
        }
        if (ptype.getSizeInBits() <= 32) {
            if (ptype.isSigned()) {
                pv.setEngValue(new SInt32Value((int) longValue));
            } else {
                pv.setEngValue(new UInt32Value((int) longValue));
            }
        } else {
            if (ptype.isSigned()) {
                pv.setEngValue(new SInt64Value(longValue));
            } else {
                pv.setEngValue(new UInt64Value(longValue));
            }
        }
    }

    private void setRawFloatValue(FloatDataEncoding fde, ParameterValue pv, Object outputValue) {
        double doubleValue;
        if (outputValue instanceof Number) {
            doubleValue = ((Number) outputValue).doubleValue();
        } else {
            pv.setAcquisitionStatus(AcquisitionStatus.INVALID);
            log.warn("Unexpected script return type for {}. Was expecting a number, but got: {}",
                    pv.getParameter().getName(), outputValue.getClass());
            return;
        }
        if (fde.getSizeInBits() <= 32) {
            pv.setRawFloatValue((float) doubleValue);
        } else {
            pv.setRawDoubleValue(doubleValue);
        }
    }

    private void setEngFloatValue(FloatParameterType ptype, ParameterValue pv, Object outputValue) {
        double doubleValue;
        if (outputValue instanceof Number) {
            doubleValue = ((Number) outputValue).doubleValue();
        } else {
            pv.setAcquisitionStatus(AcquisitionStatus.INVALID);
            log.warn("Unexpected script return type for {}. Was expecting a number, but got: {}",
                    pv.getParameter().getName(), outputValue.getClass());
            return;
        }
        if (ptype.getSizeInBits() <= 32) {
            pv.setFloatValue((float) doubleValue);
        } else {
            pv.setDoubleValue(doubleValue);
        }
    }

    @Override
    public String toString() {
        return algorithmDef.getName() +" executor "+invocable;
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

}
