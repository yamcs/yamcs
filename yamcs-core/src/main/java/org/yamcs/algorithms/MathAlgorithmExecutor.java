package org.yamcs.algorithms;

import java.util.Arrays;

import org.codehaus.commons.compiler.LocatedException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.janino.SimpleCompiler;
import org.yamcs.mdb.MathOperationCalibratorFactory;
import org.yamcs.mdb.ParameterTypeUtils;
import org.yamcs.mdb.ProcessingData;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.MathAlgorithm;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;

/**
 * Executes XTCE math algorithms {@link MathAlgorithm}
 * 
 * All the input parameters are converted to doubles and there is one single double output parameter
 * 
 * @author nm
 *
 */
public class MathAlgorithmExecutor extends AbstractAlgorithmExecutor {
    final Parameter outParam;
    final double[] input;
    final MathOperationEvaluator evaluator;

    public MathAlgorithmExecutor(Algorithm algorithmDef, AlgorithmExecutionContext execCtx, MathAlgorithm algorithm) {
        super(algorithmDef, execCtx);
        OutputParameter op = algorithmDef.getOutputList().get(0);
        outParam = op.getParameter();
        input = new double[algorithmDef.getInputList().size()];
        evaluator = getEvaluator(algorithm);
    }

    @Override
    public AlgorithmExecutionResult execute(long acqTime, long genTime, ProcessingData data) {
        ParameterValue pv = new ParameterValue(outParam);
        pv.setAcquisitionTime(acqTime);
        pv.setGenerationTime(genTime);
        double value = evaluator.evaluate(input);
        Value engValue = ParameterTypeUtils.getEngValue(outParam.getParameterType(), Double.valueOf(value));
        if (engValue == null) {
            execCtx.getProcessorData().getEventProducer()
                    .sendWarning(getAlgorithm().getName(), "Cannot convert raw value from algorithm output "
                            + "'" + value + "' into " + outParam.getParameterType());
            pv.setAcquisitionStatus(AcquisitionStatus.INVALID);
        } else {
            pv.setEngValue(engValue);
        }
        return new AlgorithmExecutionResult(inputValues, value, Arrays.asList(pv));
    }

    @Override
    protected void updateInput(int idx, InputParameter inputParameter, ParameterValue newValue) {
        Value v = inputParameter.getParameterInstance().useCalibratedValue() ? newValue.getEngValue()
                : newValue.getRawValue();

        if (v == null) {
            log.warn("Received null value for input parameter {}", inputParameter);
            return;
        }

        if (!ValueUtility.processAsDouble(v, d -> {
            input[idx] = d;
        })) {
            log.warn("Received null value for input parameter {}", inputParameter);
        }
    }

    private MathOperationEvaluator getEvaluator(MathAlgorithm algo) {
        StringBuilder sb = new StringBuilder();
        String className = "MathOperationEvaluator" + algo.hashCode();
        sb.append("package org.yamcs.algorithms.maeval;\n")
                .append("public class ").append(className)
                .append(" implements org.yamcs.algorithms.MathOperationEvaluator {\n")
                .append("   public double evaluate(double[] input) {\n")
                .append("       return ")
                .append(MathOperationCalibratorFactory.getJavaExpression(algo.getOperation(), algo.getInputList()))
                .append(";\n")
                .append("   }\n")
                .append("}\n");
        String expr = sb.toString();
        log.debug("Compiling math operation converted to java:\n {}", expr);
        try {
            SimpleCompiler compiler = new SimpleCompiler();
            compiler.cook(expr);
            Class<?> cexprClass = compiler.getClassLoader().loadClass("org.yamcs.algorithms.maeval." + className);
            return (MathOperationEvaluator) cexprClass.newInstance();
        } catch (LocatedException e) {
            String msg = e.getMessage();
            Location l = e.getLocation();
            if (l != null) {
                // we change the location in the message because it refers to the fabricated code
                // it is still not perfect, if the expression is not properly closed
                // , janino will complain about the next line that the user doesn't know about...
                Location l1 = new Location(null, (short) (l.getLineNumber() - 3), (short) (l.getColumnNumber() - 7));
                msg = l1.toString() + ": " + msg.substring(l.toString().length() + 1);
            }
            throw new IllegalArgumentException("Cannot compile math operation converted to java:\n"
                    + expr + "" + msg, e);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot compile math operation converted to java:\n"
                    + expr, e);
        }
    }

}
