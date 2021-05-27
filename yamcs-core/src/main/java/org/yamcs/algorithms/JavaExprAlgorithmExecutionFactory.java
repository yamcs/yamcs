package org.yamcs.algorithms;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import org.codehaus.commons.compiler.LocatedException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.janino.SimpleCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.Algorithm.Scope;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.OutputParameter;

/**
 * Generates executors for java-expression algorithms.
 * <p>
 * Each algorithm gets a class with the following body
 * 
 * <pre>
 * class AlgorithmExecutor_algoName extends AbstractAlgorithmExecutor {
 * 
 *     AlgorithmExecutionResult execute(long acqTime, long genTime) throws AlgorithmException {
 *        List&lt;ParameterValue&gt; outputValues = new ArrayList&lt;&gt;();
 *        for(int i = 0; i &lt; algorithmDef.getOutputList().size; i++) {
 *           outputValues.add(new ParameterValue());
 *        }
 *        execute_java_expr(inputValues.get(0), inputValues.get(1), ...);
 *     }
 * 
 *     void execute_java_expr(
 *         ParameterValue [input_name1],
 *         ParameterValue [input_name2],
 *         ...,
 *         ParameterValue [output_name1],
 *         ParameterValue [output_name2],
 *         ...
 *     ) throws AlgorithmException {
 *           [algorithm_text]
 *     }
 * }
 * </pre>
 * 
 * Where the input_nameX and output_nameY are the names of the inputs respectively outputs given in the algorithm
 * definition and the algorithm_text is the text given in the algorithm definition.
 * <p>
 * The types of the inputs and outputs are {@link ParameterValue}
 * <p>
 * The output parameter generation time are initialised with the generation time of the parameter that triggered the
 * algorithm but can be changed in the algorithm text.
 * 
 * 
 */
public class JavaExprAlgorithmExecutionFactory implements AlgorithmExecutorFactory {
    static final Logger log = LoggerFactory.getLogger(ScriptAlgorithmExecutorFactory.class);

    @Override
    public AlgorithmExecutor makeExecutor(CustomAlgorithm alg, AlgorithmExecutionContext execCtx)
            throws AlgorithmException {
        String className = alg.getQualifiedName().replace("/", "_");

        String code = generateClassCode(className, alg);
        try {
            log.debug("Compiling:\n{}", code);
            SimpleCompiler compiler = new SimpleCompiler();
            compiler.cook(code);
            Class<? extends AlgorithmExecutor> cexprClass = (Class<? extends AlgorithmExecutor>) compiler
                    .getClassLoader()
                    .loadClass("org.yamcs.algorithms.javaexpr." + className);

            Constructor<? extends AlgorithmExecutor> constructor = cexprClass
                    .getConstructor(CustomAlgorithm.class, AlgorithmExecutionContext.class);
            return constructor.newInstance(alg, execCtx);
        } catch (LocatedException e) {
            String msg = e.getMessage();
            Location l = e.getLocation();
            if (l != null) {
                // we change the location in the message because it refers to the fabricated code
                // it is still not perfect, if the expression is not properly closed
                // , janino will complain about the next line that the user doesn't know about...
                // TODO: keep track automatically of the line number
                Location l1 = new Location(null, (short) (l.getLineNumber() - 25), l.getColumnNumber());
                msg = l1.toString() + ": " + msg.substring(l.toString().length() + 1);
            }
            throw new AlgorithmException("Cannot compile expression '" + alg.getAlgorithmText() + "': " + msg, e);
        } catch (Exception e) {
            throw new AlgorithmException("Cannot compile expression '" + alg.getAlgorithmText() + "'", e);
        }
    }

    @Override
    public List<String> getLanguages() {
        return Arrays.asList("java-expression", "Java-expression", "Java-Expression");
    }

    public static String generateClassCode(String className, CustomAlgorithm algorithmDef) {
        StringBuilder sb = new StringBuilder();
        sb.append("package org.yamcs.algorithms.javaexpr;\n\n");

        sb.append("import java.util.*;\n");
        sb.append("import org.yamcs.xtce.CustomAlgorithm;\n");
        sb.append("import org.yamcs.xtce.OutputParameter;\n");
        sb.append("import org.yamcs.parameter.ParameterValue;\n");
        sb.append("import org.yamcs.parameter.Value;\n");
        sb.append("import org.yamcs.commanding.ArgumentValue;\n");
        sb.append("import org.yamcs.algorithms.AlgorithmExecutionResult;\n");
        sb.append("import org.yamcs.algorithms.AbstractJavaExprExecutor;\n");
        sb.append("import org.yamcs.algorithms.AlgorithmExecutionContext;\n");
        sb.append("import org.yamcs.algorithms.AlgorithmException;\n");

        sb.append("\n");
        sb.append("public class ").append(className).append(" extends AbstractJavaExprExecutor {\n");
        sb.append("    public ").append(className)
                .append("(CustomAlgorithm algorithmDef, AlgorithmExecutionContext execCtx) {\n"
                        + "        super(algorithmDef, execCtx);\n"
                        + "    }\n\n");

        sb.append("    public Object doExecute(long acqTime, long genTime, List outputValues) "
                + "throws AlgorithmException {\n");
        sb.append("        Object result = null;\n");
        if (algorithmDef.getScope() == Scope.COMMAND_VERIFICATION) {
            sb.append("        result = ");
        }
        sb.append("        execute_java_expr(");

        boolean first = true;
        List<InputParameter> inputList = algorithmDef.getInputList();
        for (int i = 0; i < inputList.size(); i++) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            InputParameter inputParam = inputList.get(i);
            if (inputParam.getParameterInstance() != null) {
                sb.append("(ParameterValue) ");
            } else {
                sb.append("(ArgumentValue) ");
            }
            sb.append("inputValues.get(").append(i).append(")");
        }

        for (int i = 0; i < algorithmDef.getOutputList().size(); i++) {
            sb.append(", ");
            sb.append("(ParameterValue) outputValues.get(").append(i).append(")");
        }
        sb.append(");\n");
        sb.append("        return result;\n");
        sb.append("    }\n\n");
        if (algorithmDef.getScope() == Scope.COMMAND_VERIFICATION) {
            sb.append("    private Object execute_java_expr(");
        } else {
            sb.append("    private void execute_java_expr(");
        }

        first = true;
        for (InputParameter inputParam : algorithmDef.getInputList()) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            if (inputParam.getParameterInstance() != null) {
                sb.append("ParameterValue ").append(inputParam.getEffectiveInputName());
            } else {
                sb.append("ArgumentValue ").append(inputParam.getEffectiveInputName());
            }
        }

        for (OutputParameter outputParam : algorithmDef.getOutputList()) {
            sb.append(", ");
            sb.append("ParameterValue ").append(outputParam.getEffectiveOutputName());
        }

        sb.append(") {\n");
        sb.append(algorithmDef.getAlgorithmText()).append("\n");
        sb.append("    }\n\n");

        sb.append("}");

        return sb.toString();
    }

}
