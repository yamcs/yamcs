package org.yamcs.mdb;

import java.util.List;
import java.util.Stack;

import org.codehaus.commons.compiler.LocatedException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.janino.SimpleCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.MathOperation;
import org.yamcs.xtce.MathOperation.ElementType;
import org.yamcs.xtce.MathOperationCalibrator;
import org.yamcs.xtce.MathOperator;
import org.yamcs.xtce.ParameterInstanceRef;

public class MathOperationCalibratorFactory {
    protected static final Logger log = LoggerFactory.getLogger(MathOperationCalibratorFactory.class);

    /**
     * Compiles the math operation calibrator into an executable calibrator processor
     * 
     * @param c
     * @return a calibrator processor
     * @throws IllegalArgumentException
     *             if the expression cannot be compiled
     */
    public static CalibratorProc compile(MathOperationCalibrator c) {
        StringBuilder sb = new StringBuilder();
        String className = "Expression" + c.hashCode();
        sb.append("package org.yamcs.mdb.mocf;\n")
                .append("public class ").append(className).append(" implements org.yamcs.mdb.CalibratorProc {\n")
                .append("   public double calibrate(double v) {\n")
                .append("       return ").append(getJavaExpression(c, null)).append(";\n")
                .append("   }\n")
                .append("}\n");
        String expr = sb.toString();
        log.debug("Compiling math operation converted to java:\n {}", expr);
        try {
            SimpleCompiler compiler = new SimpleCompiler();
            compiler.cook(expr);
            Class<?> cexprClass = compiler.getClassLoader().loadClass("org.yamcs.mdb.mocf." + className);
            return (CalibratorProc) cexprClass.newInstance();
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
                    + expr + "\n" + msg, e);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot compile math operation converted to java:\n"
                    + expr, e);
        }
    }

    public static String getJavaExpression(MathOperation operation, List<InputParameter> inputParams) {
        Stack<String> stack = new Stack<>();
        for (MathOperation.Element e : operation.getElementList()) {
            ElementType type = e.getType();
            if (type == ElementType.OPERATOR) {
                MathOperator mo = e.getOperator();
                if (mo == MathOperator.SWAP) {
                    // x1 x2 -- x2 x1
                    String x2 = stack.pop();
                    String x1 = stack.pop();
                    stack.push(x2);
                    stack.push(x1);
                } else if (mo == MathOperator.DROP) {
                    stack.pop();
                } else if (mo == MathOperator.DUP) {
                    stack.push(stack.peek());
                } else if (mo == MathOperator.OVER) {
                    // x1 x2 -- x1 x2 x1
                    String x2 = stack.pop();
                    String x1 = stack.pop();
                    stack.push(x1);
                    stack.push(x2);
                    stack.push(x1);
                } else {
                    if (mo.getArity() == 1) {
                        String l = stack.pop();
                        stack.push(getJavaExpression(mo, l, null));
                    } else if (mo.getArity() == 2) {
                        String r = stack.pop();
                        String l = stack.pop();
                        stack.push(getJavaExpression(mo, l, r));
                    } else {
                        throw new IllegalStateException("Only arity 1 and 2 supported");
                    }
                }
            } else if (type == ElementType.THIS_PARAMETER_OPERAND) {
                stack.push("v");
            } else if (type == ElementType.VALUE_OPERAND) {
                stack.push(e.toString());
            } else if (type == ElementType.PARAMETER_INSTANCE_REF_OPERAND) {
                if (inputParams == null) {
                    throw new IllegalArgumentException(
                            "Reference to parameter encountered but no input list was provided");
                }
                int idx = findParameter(inputParams, e.getParameterInstanceRef());
                if (idx == -1) {
                    throw new IllegalArgumentException("Reference to parameter " + e.getParameterInstanceRef()
                            + " encountered but that parameter is not in the input list: " + inputParams);
                }
                stack.push("input[" + idx + "]");
            } else {
                throw new UnsupportedOperationException(" MathOperations operans of type " + type + " not supported");
            }
        }

        if (stack.size() != 1) {
            throw new IllegalArgumentException(
                    "Invalid MathOperation (stack is not empty at the end of the processing)");
        }
        return stack.get(0);
    }

    private static int findParameter(List<InputParameter> inputParams, ParameterInstanceRef parameterInstanceRef) {
        for (int i = 0; i < inputParams.size(); i++) {
            if (inputParams.get(i).getParameterInstance() == parameterInstanceRef) {
                return i;
            }
        }
        return -1;
    }

    static String getJavaExpression(MathOperator op, String l, String r) {
        switch (op) {
        case PLUS:
        case MINUS:
        case DIV:
        case STAR:
        case MODULO:
            return "(" + l + " " + op.xtceName() + " " + r + ")";
        case ACOS:
        case SIN:
        case ASIN:
        case ATAN:
        case TANH:
        case ABS:
        case COS:
        case COSH:
        case SINH:
        case TAN:
            return "Math." + op.xtceName() + "(" + l + ")";
        case ACOSH:
        case ASINH:
        case ATANH:
            return "org.yamcs.utils.MathUtil." + op.xtceName() + "(" + l + ")";
        case LOG:
            return "Math.log10(" + l + ")";
        case LN:
            return "Math.log(" + l + ")";
        case EXP:
            return "Math.exp(" + l + ")";
        case POW:
            return "Math.pow(" + l + "," + r + ")";
        case REVPOW:
            return "Math.pow(" + r + "," + l + ")";
        case INV:
            return "(" + 1 + " / " + l + ")";
        case FACT:
            return "org.yamcs.utils.MathUtil.factorial(" + l + ")";
        case LEFT_SHIFT:
        case RIGHT_SHIFT:
        case BITWISE_AND:
        case BITWISE_OR:
            return "((long)" + l + " " + op.xtceName() + " (long)" + r + ")";
        default:
            throw new UnsupportedOperationException(op + " not implemented");
        }
    }

}
