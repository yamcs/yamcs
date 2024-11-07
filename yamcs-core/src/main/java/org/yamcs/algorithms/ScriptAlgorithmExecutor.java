package org.yamcs.algorithms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.Invocable;
import javax.script.ScriptException;

import org.codehaus.janino.SimpleCompiler;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.commanding.ArgumentValue;
import org.yamcs.events.EventProducer;
import org.yamcs.mdb.DataEncodingDecoder;
import org.yamcs.mdb.ParameterTypeProcessor;
import org.yamcs.mdb.ParameterTypeUtils;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.RawEngValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.time.Instant;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.AbsoluteTimeDataType;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.StringParameterType;

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
    // stores both the function inputs and outputs
    // the position of the inputs corresponds to the position of AlgorithmDef input respectively output List
    final Object[] functionArgs;

    final int numInputs;
    final int numOutputs;

    // Each ValueBinding class represent a unique raw/eng type combination (== key)
    private static Map<String, Class<ValueBinding>> valueBindingClasses = Collections
            .synchronizedMap(new HashMap<>());
    ParameterTypeProcessor parameterTypeProcessor;
    final String functionName;
    final EventProducer eventProducer;
    final String functionScript;

    public ScriptAlgorithmExecutor(CustomAlgorithm algorithmDef, Invocable invocable, String functionName,
            String functionScript, AlgorithmExecutionContext execCtx) {
        super(algorithmDef, execCtx);
        this.parameterTypeProcessor = new ParameterTypeProcessor(execCtx.getProcessorData());
        this.functionName = functionName;
        this.invocable = invocable;
        this.eventProducer = execCtx.getEventProducer();
        this.functionScript = functionScript;

        numInputs = algorithmDef.getInputList().size();
        List<OutputParameter> outputList = algorithmDef.getOutputList();
        numOutputs = outputList.size();
        functionArgs = new Object[numInputs + numOutputs];

        // Set empty output bindings so that algorithms can write their attributes
        for (int k = 0; k < numOutputs; k++) {
            functionArgs[numInputs + k] = new OutputValueBinding();
        }
    }

    @Override
    protected void updateInput(int position, InputParameter inputParameter, ParameterValue newValue) {
        doUpdateInput(position, inputParameter, newValue);
    }

    @Override
    protected void updateInputArgument(int position, InputParameter inputParameter, ArgumentValue newValue) {
        doUpdateInput(position, inputParameter, newValue);
    }

    private void doUpdateInput(int position, InputParameter inputParameter, RawEngValue newValue) {
        ValueBinding valueBinding = (ValueBinding) functionArgs[position];
        // First time for an inputParameter, it will create a ValueBinding object.
        // Further calls will just update that object
        if (valueBinding == null) {
            valueBinding = toValueBinding(newValue);
            functionArgs[position] = valueBinding;
        }
        if (valueBinding == null) {
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Algo {} updating input {} with value {}", algorithmDef.getName(),
                    inputParameter.getEffectiveInputName(), newValue);
        }
        valueBinding.updateValue(newValue);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.yamcs.algorithms.AlgorithmExecutor#runAlgorithm(long, long)
     */
    @Override
    public synchronized AlgorithmExecutionResult execute(long acqTime, long genTime, ProcessingData data) {
        if (log.isTraceEnabled()) {
            logTraceInput();
        }
        try {
            for (int k = 0; k < numOutputs; k++) {
                OutputValueBinding outvb = (OutputValueBinding) functionArgs[numInputs + k];
                outvb.value = null;
                outvb.rawValue = null;
            }

            Object returnValue = invocable.invokeFunction(functionName, functionArgs);

            if (log.isTraceEnabled()) {
                logTraceOutput(returnValue);
            }

            List<ParameterValue> outputValues = new ArrayList<>();
            List<OutputParameter> outputList = algorithmDef.getOutputList();
            for (int k = 0; k < numOutputs; k++) {
                OutputParameter outputParameter = outputList.get(k);
                OutputValueBinding res = (OutputValueBinding) functionArgs[numInputs + k];
                if (res.updated && (res.value != null || res.rawValue != null)) {
                    ParameterValue pv = convertScriptOutputToParameterValue(outputParameter.getParameter(), res);
                    pv.setAcquisitionTime(acqTime);
                    pv.setGenerationTime(genTime);
                    outputValues.add(pv);
                }
            }

            return new AlgorithmExecutionResult(inputValues, returnValue, outputValues);
        } catch (ScriptException e) {
            String msg = getError(e);
            throw new AlgorithmException(inputValues, msg);
        } catch (NoSuchMethodException e) {
            throw new AlgorithmException("Error while executing algorithm: " + e.getMessage());
        } catch (InvalidAlgorithmOutputException e) {
            eventProducer.sendWarning(getAlgorithm().getName(), e.getMessage());
            throw new AlgorithmException(inputValues, e.getMessage());
        }
    }

    String getError(ScriptException e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getMessage());
        String line = getLine(functionScript, e.getLineNumber());
        if (line != null) {
            sb.append(":\n").append(line).append("\n");
            if (e.getColumnNumber() >= 0) {
                for (int i = 0; i < e.getColumnNumber(); i++) {
                    sb.append(" ");
                }
                sb.append("^");
            }
        }

        return sb.toString();
    }

    private String getLine(String script, int lineNumber) {
        int n = 0;
        try (BufferedReader bufReader = new BufferedReader(new StringReader(script))) {
            String line;
            while ((line = bufReader.readLine()) != null) {
                if (++n == lineNumber) {
                    return line;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    private void logTraceInput() {
        StringBuilder sb = new StringBuilder();
        sb.append("Running algorithm ").append(algorithmDef.getName())
                .append("( ");
        int pos = 0;
        for (InputParameter p : algorithmDef.getInputList()) {
            if (pos != 0) {
                sb.append(", ");
            }
            sb.append(p.getEffectiveInputName()).append(": ")
                    .append(String.valueOf(functionArgs[pos]));
            pos++;
        }
        sb.append(")");
        log.trace(sb.toString());
    }

    private void logTraceOutput(Object returnValue) {
        StringBuilder sb = new StringBuilder();
        sb.append("algorithm ").append(algorithmDef.getName())
                .append(" outputs: ( ");
        int pos = 0;
        for (OutputParameter p : algorithmDef.getOutputList()) {
            if (pos != 0) {
                sb.append(", ");
            }
            sb.append(p.getOutputName()).append(": ")
                    .append(String.valueOf(functionArgs[numInputs + pos]));
            pos++;
        }
        sb.append(") returnValue: ").append(String.valueOf(returnValue));
        log.trace(sb.toString());
    }

    /**
     * converts the output of the algorithm to a value corresponding to a parameter type
     * <p>
     * Throws InvalidAlgorithmOutputException if the conversion cannot be made
     */
    private ParameterValue convertScriptOutputToParameterValue(Parameter parameter, OutputValueBinding binding)
            throws InvalidAlgorithmOutputException {
        ParameterValue pval = new ParameterValue(parameter);
        ParameterType ptype = parameter.getParameterType();
        DataEncoding de = null;

        if (binding.rawValue != null) {
            if (ptype instanceof BaseDataType) {
                de = ((BaseDataType) ptype).getEncoding();
            }

            if (de != null) {
                Value rawV = DataEncodingDecoder.getRawValue(de, binding.rawValue);
                if (rawV == null) {
                    throw new InvalidAlgorithmOutputException(parameter, binding,
                            "Cannot convert raw value from algorithm output "
                                    + "'" + binding.value + "' of type " + binding.value.getClass()
                                    + " into values for the data encoding " + de);
                } else {
                    pval.setRawValue(rawV);
                    if (binding.value == null) {
                        parameterTypeProcessor.calibrate(pval);
                    }
                }
            } else {
                throw new InvalidAlgorithmOutputException(parameter, binding, "Algorithm provided raw value"
                        + " but the parameter has no data encoding");
            }
        }

        if (binding.value != null) {
            Value v = getEngValue(ptype, binding.value);
            if (v == null) {
                throw new InvalidAlgorithmOutputException(parameter, binding,
                        "Cannot convert algorithm output value "
                                + "'" + binding.value + "' of type " + binding.value.getClass().getSimpleName()
                                + " into values for the type "
                                + ptype.getQualifiedName() + "(" + ptype.getClass().getSimpleName() + ")");
            } else {
                pval.setEngValue(v);
            }
        }
        return pval;
    }

    private ValueBinding toValueBinding(RawEngValue pval) {
        try {
            Class<ValueBinding> clazz = getOrCreateValueBindingClass(pval);
            if (clazz == null) {
                return null;
            }
            Constructor<ValueBinding> constructor = clazz.getConstructor();
            return constructor.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Could not instantiate object of custom class", e);
        }
    }

    private Class<ValueBinding> getOrCreateValueBindingClass(RawEngValue pval) {

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
            source.append("import " + RawEngValue.class.getName() + ";\n")
                    .append("public class " + className + " extends ValueBinding {\n");
            StringBuilder updateValueSource = new StringBuilder("  public void updateValue(RawEngValue v) {\n")
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
                    log.trace("Compiling this:\n{}\n", source);
                }

                compiler.cook(source.toString());
                @SuppressWarnings("unchecked")
                Class<ValueBinding> clazz = (Class<ValueBinding>) compiler.getClassLoader()
                        .loadClass("org.yamcs.algorithms." + className);
                valueBindingClasses.put(key, clazz);
                return clazz;
            } catch (Exception e) {
                throw new IllegalStateException("Could not compile custom class: " + source.toString(), e);
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
                source.append("  public byte[] rawValue;\n");
                return "    rawValue=v.getRawValue().getBinaryValue();\n";
            } else {
                source.append("  public byte[] value;\n");
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
                source.append("  public long rawValue;\n");
                return "    rawValue=(long)Integer.toUnsignedLong(v.getRawValue().getUint32Value());\n";
            } else {
                source.append("  public long value;\n");
                return "    value=(long)Integer.toUnsignedLong(v.getEngValue().getUint32Value());\n";
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
        } else if (v.getType() == Type.ENUMERATED) {
            if (raw) {
                throw new IllegalArgumentException("Unexpected raw value of type ENUMERATED");
            } else {
                source.append("  public String value;\n");
                return "    value=v.getEngValue().getStringValue();\n";
            }
        } else if (v.getType() == Type.TIMESTAMP) {
            if (raw) {
                source.append("  public org.yamcs.time.Instant rawValue;\n");
                return "    rawValue=org.yamcs.time.Instant.get(v.getRawValue().getTimestampValue());\n";
            } else {
                source.append("  public org.yamcs.time.Instant value;\n");
                return "    value=org.yamcs.time.Instant.get(v.getEngValue().getTimestampValue());\n";
            }
        } else {
            throw new IllegalArgumentException("Unexpected value of type " + v.getType());
        }
    }

    @Override
    public String toString() {
        return algorithmDef.getName() + " executor " + invocable;
    }

    public static Value getEngValue(ParameterType ptype, Object value) {
        if (ptype instanceof IntegerParameterType) {
            return ParameterTypeUtils.getEngIntegerValue((IntegerParameterType) ptype, value);
        } else if (ptype instanceof FloatParameterType) {
            return ParameterTypeUtils.getEngFloatValue((FloatParameterType) ptype, value);
        } else if (ptype instanceof StringParameterType) {
            if (value instanceof String) {
                return ValueUtility.getStringValue((String) value);
            } else {
                return null;
            }
        } else if (ptype instanceof BooleanParameterType) {
            if (value instanceof Boolean) {
                return ValueUtility.getBooleanValue((Boolean) value);
            } else {
                return null;
            }
        } else if (ptype instanceof BinaryParameterType) {
            if (value instanceof byte[]) {
                return ValueUtility.getBinaryValue((byte[]) value);
            } else {
                return null;
            }
        } else if (ptype instanceof EnumeratedParameterType) {
            if (value instanceof String) {
                return ValueUtility.getStringValue((String) value);
            } else {
                return null;
            }
        } else if (ptype instanceof AbsoluteTimeDataType) {
            if (value instanceof Instant v) {
                return ValueUtility.getTimestampValue(v.getMillis());
            } else if (value instanceof String v) {
                long t = TimeEncoding.parse(v);
                return ValueUtility.getTimestampValue(t);
            } else if (value instanceof Double d) {
                return ValueUtility.getTimestampValue(d.longValue());
            } else if ((value instanceof ScriptObjectMirror som) && "Date".equals(som.getClassName())) {
                long unixTime = ((Double) som.callMember("getTime")).longValue();
                return ValueUtility.getTimestampValue(TimeEncoding.fromUnixMillisec(unixTime));
            } else {
                return null;
            }
        } else {
            throw new IllegalStateException("Unknown parameter type '" + ptype + "'");
        }
    }

}
