package org.yamcs.mdb;

import static org.yamcs.xtce.MatchCriteria.printExpressionReference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.yamcs.commanding.ArgumentValue;
import org.yamcs.logging.Log;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.xtce.ANDedConditions;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentInstanceRef;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.Condition;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.DataType;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.ORedConditions;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterOrArgumentRef;
import org.yamcs.xtce.PathElement;
import org.yamcs.xtce.util.DataTypeUtil;

import com.google.common.primitives.UnsignedBytes;
import com.google.common.primitives.UnsignedLongs;

public class MatchCriteriaEvaluatorFactory {
    private static Log log = new Log(MatchCriteriaEvaluatorFactory.class);

    public static final MatchCriteriaEvaluator getEvaluator(MatchCriteria matchCriteria) {
        if (matchCriteria instanceof Comparison) {
            Comparison comp = (Comparison) matchCriteria;
            return new RefValueEvaluator(comp.getRef(), comp.getComparisonOperator(), comp.getStringValue());
        } else if (matchCriteria instanceof ComparisonList) {
            return new ANDedConditionsEvaluator((ComparisonList) matchCriteria);
        } else if (matchCriteria instanceof Condition) {
            Condition condition = (Condition) matchCriteria;
            if (condition.getRightValue() != null) {
                return new RefValueEvaluator(condition.getLeftRef(), condition.getComparisonOperator(),
                        condition.getRightValue());
            } else {
                return new RefRefEvaluator(condition.getLeftRef(), condition.getComparisonOperator(),
                        condition.getRightRef());
            }
        } else if (matchCriteria instanceof ANDedConditions) {
            return new ANDedConditionsEvaluator((ANDedConditions) matchCriteria);
        } else if (matchCriteria instanceof ORedConditions) {
            return new ORedConditionsEvaluator((ORedConditions) matchCriteria);
        } else {
            throw new IllegalStateException("Unknown matchcriteria type " + matchCriteria.getClass());
        }
    }

    static abstract class AbstractComparisonEvaluator implements MatchCriteriaEvaluator {

        final OperatorType comparisonOperator;

        abstract ResolvedValue resolveLeft(ProcessingData input);

        abstract ResolvedValue resolveRight(ProcessingData input);

        AbstractComparisonEvaluator(OperatorType comparisonOperator) {
            this.comparisonOperator = comparisonOperator;
        }

        @Override
        public MatchResult evaluate(ProcessingData input) {
            ResolvedValue lValue = resolveLeft(input);
            ResolvedValue rValue = resolveRight(input);

            if ((lValue == null) || (rValue == null)) {
                return MatchResult.UNDEF;
            }
            boolean result = false;

            if (lValue.evaluator == rValue.evaluator) {
                result = lValue.evaluator.evaluate(comparisonOperator, lValue, rValue);
            } else {
                if ((lValue.evaluator == intEvaluator) && (lValue.evaluator == floatEvaluator)) {
                    lValue.value = (double) ((long) lValue.value);
                    result = floatEvaluator.evaluate(comparisonOperator, lValue, rValue);
                } else if ((lValue.evaluator == floatEvaluator) && (lValue.evaluator == intEvaluator)) {
                    rValue.value = (double) ((long) rValue.value);
                    result = floatEvaluator.evaluate(comparisonOperator, lValue, rValue);
                } else {
                    log.error("Comparing values of incompatible types: "
                            + lValue.evaluator.getComparedType()
                            + " vs. " + rValue.evaluator.getComparedType());
                }
            }

            return result ? MatchResult.OK : MatchResult.NOK;
        }

        static String printExpressionValue(Object value) {
            if (value instanceof String) {
                // Need to allow for quotes and slashes within the string itself
                // Turn '\' into '\\' and next, '"' into '\"'
                String escaped = ((String) value).replace("\\", "\\\\").replace("\"", "\\\"");
                return "\"" + escaped + "\"";
            } else {
                return String.valueOf(value);
            }
        }
    }

    // evaluates [reference operator value] conditions
    static class RefValueEvaluator extends AbstractComparisonEvaluator {

        ParameterOrArgumentRef ref;
        ResolvedValue rValue;

        public RefValueEvaluator(ParameterOrArgumentRef ref, OperatorType comparisonOperator,
                String stringValue) {
            super(comparisonOperator);
            this.ref = ref;

            try {
                DataType dtype = ref.getDataType();
                if (ref.getMemberPath() != null) {
                    dtype = DataTypeUtil.getMemberType(dtype, ref.getMemberPath());
                }
                if (ref.useCalibratedValue()) {
                    rValue = resolveValue(dtype.convertType(stringValue));
                } else {
                    rValue = resolveValue(dtype.parseStringForRawValue(stringValue));
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Cannot parse value required for comparing with "
                        + ref.getName() + ": " + e.getMessage(), e);
            }
        }

        @Override
        ResolvedValue resolveLeft(ProcessingData input) {
            return resolveValue(ref, input);
        }

        @Override
        ResolvedValue resolveRight(ProcessingData input) {
            return rValue;
        }

        @Override
        public String toExpressionString() {
            return printExpressionReference(ref) + " "
                    + comparisonOperator + " "
                    + printExpressionValue(rValue.value);
        }
    }

    static class RefRefEvaluator extends AbstractComparisonEvaluator {
        ParameterOrArgumentRef leftRef;
        ParameterOrArgumentRef rightRef;

        public RefRefEvaluator(ParameterOrArgumentRef leftRef, OperatorType comparisonOperator,
                ParameterOrArgumentRef rightRef) {
            super(comparisonOperator);
            this.leftRef = leftRef;
            this.rightRef = rightRef;
        }

        @Override
        ResolvedValue resolveLeft(ProcessingData input) {
            return resolveValue(leftRef, input);
        }

        @Override
        ResolvedValue resolveRight(ProcessingData input) {
            return resolveValue(rightRef, input);
        }

        @Override
        public String toExpressionString() {
            return printExpressionReference(leftRef) + " "
                    + comparisonOperator + " "
                    + printExpressionValue(rightRef);
        }
    }

    static class ANDedConditionsEvaluator implements MatchCriteriaEvaluator {
        final List<MatchCriteriaEvaluator> evaluatorList;

        public ANDedConditionsEvaluator(ANDedConditions conditions) {
            evaluatorList = new ArrayList<>(conditions.getExpressionList().size());
            for (MatchCriteria mc : conditions.getExpressionList()) {
                evaluatorList.add(MatchCriteriaEvaluatorFactory.getEvaluator(mc));
            }
        }

        public ANDedConditionsEvaluator(ComparisonList comparisonList) {
            evaluatorList = new ArrayList<>(comparisonList.getComparisonList().size());
            for (MatchCriteria mc : comparisonList.getComparisonList()) {
                evaluatorList.add(MatchCriteriaEvaluatorFactory.getEvaluator(mc));
            }
        }

        @Override
        public MatchResult evaluate(ProcessingData input) {
            MatchResult result = MatchResult.OK;

            for (MatchCriteriaEvaluator mce : evaluatorList) {
                MatchResult r = mce.evaluate(input);
                if (r == MatchResult.NOK) {
                    result = r;
                    break;
                } else if (r == MatchResult.UNDEF) {
                    result = r;
                    // continue checking maybe a comparison will return NOK
                }
            }

            return result;
        }

        @Override
        public String toExpressionString() {
            if (evaluatorList.size() == 1) {
                return evaluatorList.get(0).toExpressionString();
            } else {
                return evaluatorList.stream()
                        .map(evaluator -> "(" + evaluator.toExpressionString() + ")")
                        .collect(Collectors.joining(" && "));
            }
        }
    }

    static class ORedConditionsEvaluator implements MatchCriteriaEvaluator {
        final List<MatchCriteriaEvaluator> evaluatorList;

        public ORedConditionsEvaluator(ORedConditions conditions) {
            evaluatorList = new ArrayList<>(conditions.getExpressionList().size());
            for (MatchCriteria mc : conditions.getExpressionList()) {
                evaluatorList.add(MatchCriteriaEvaluatorFactory.getEvaluator(mc));
            }
        }

        @Override
        public MatchResult evaluate(ProcessingData input) {
            MatchResult result = MatchResult.NOK;

            for (MatchCriteriaEvaluator mce : evaluatorList) {
                MatchResult r = mce.evaluate(input);
                if (r == MatchResult.OK) {
                    result = r;
                    break;
                } else if (r == MatchResult.UNDEF) {
                    result = r;
                    // continue checking maybe a expression will return OK
                }
            }
            return result;
        }

        @Override
        public String toExpressionString() {
            if (evaluatorList.size() == 1) {
                return evaluatorList.get(0).toExpressionString();
            } else {
                return evaluatorList.stream()
                        .map(evaluator -> "(" + evaluator.toExpressionString() + ")")
                        .collect(Collectors.joining(" || "));
            }
        }
    }

    static ResolvedValue resolveValue(Object value) {
        if (value instanceof Integer) {
            return new ResolvedValue(((Integer) value).longValue(), false, intEvaluator);
        } else if (value instanceof Long) {
            return new ResolvedValue((Long) value, false, intEvaluator);
        } else if (value instanceof Float) {
            return new ResolvedValue(((Float) value).doubleValue(), false, floatEvaluator);
        } else if (value instanceof Double) {
            return new ResolvedValue((Double) value, false, floatEvaluator);
        } else if (value instanceof String) {
            return new ResolvedValue((String) value, false, stringEvaluator);
        } else if (value instanceof byte[]) {
            return new ResolvedValue((byte[]) value, false, binaryEvaluator);
        } else if (value instanceof Boolean) {
            return new ResolvedValue((Boolean) value, false, booleanEvaluator);
        } else {
            log.error("Unknown value type '{}' while evaluating condition", value.getClass());
            return null;
        }
    }

    static ResolvedValue resolveValue(ParameterOrArgumentRef ref, ProcessingData input) {
        if (ref instanceof ParameterInstanceRef) {
            return resolveParameter((ParameterInstanceRef) ref, input);
        } else {
            return resolveArgument((ArgumentInstanceRef) ref, input.cmdArgs);
        }
    }

    static ResolvedValue resolveParameter(ParameterInstanceRef paramRef, ProcessingData input) {
        ParameterValue pv = null;
        Parameter p = paramRef.getParameter();
        if (p.getDataSource() == DataSource.COMMAND || p.getDataSource() == DataSource.COMMAND_HISTORY) {
            if (input.cmdParams != null) {
                pv = input.cmdParams.getLastInserted(p);
            }
            if (pv == null) {
                pv = input.cmdParamsCache.getValue(p);
            }
        } else {
            if (input.tmParams != null) {
                pv = input.tmParams.getLastInserted(p);
            }
            if (pv == null) {
                pv = input.tmParamsCache.getValue(p);
            }
        }
        if (pv == null) {
            return null;
        }

        Value v;
        if (paramRef.useCalibratedValue()) {
            v = pv.getEngValue();
        } else {
            v = pv.getRawValue();
        }
        if ((v instanceof AggregateValue) || (v instanceof ArrayValue)) {
            PathElement[] path = paramRef.getMemberPath();
            if (path == null) {
                return null;
            }
            v = AggregateUtil.getMemberValue(v, path);
        }
        return getResolvedValue(v);
    }

    static ResolvedValue resolveArgument(ArgumentInstanceRef argRef, Map<Argument, ArgumentValue> cmdArgs) {
        Argument arg = argRef.getArgument();
        ArgumentValue argv = cmdArgs.get(arg);

        if (argv == null) {
            return null;
        }

        Value v;
        if (argRef.useCalibratedValue()) {
            v = argv.getEngValue();
        } else {
            v = argv.getRawValue();
        }
        if ((v instanceof AggregateValue) || (v instanceof ArrayValue)) {
            PathElement[] path = argRef.getMemberPath();
            if (path == null) {
                return null;
            }
            v = AggregateUtil.getMemberValue(v, path);
        }
        return getResolvedValue(v);
    }

    static ResolvedValue getResolvedValue(Value v) {
        if (v == null) {
            return null;
        }
        switch (v.getType()) {
        case SINT32:
            return new ResolvedValue((long) v.getSint32Value(), false, intEvaluator);
        case SINT64:
            return new ResolvedValue(v.getSint64Value(), false, intEvaluator);
        case UINT32:
            return new ResolvedValue((long) v.getUint32Value() & 0xFFFFFFFFFFFFFFFFL, true, intEvaluator);
        case UINT64:
            return new ResolvedValue(v.getUint64Value(), true, intEvaluator);
        case FLOAT:
            return new ResolvedValue((double) v.getFloatValue(), false, floatEvaluator);
        case DOUBLE:
            return new ResolvedValue(v.getDoubleValue(), false, floatEvaluator);
        case STRING:
        case ENUMERATED:
            return new ResolvedValue(v.getStringValue(), false, stringEvaluator);
        case BINARY:
            return new ResolvedValue(v.getBinaryValue(), false, binaryEvaluator);
        case BOOLEAN:
            return new ResolvedValue(v.getBooleanValue(), false, booleanEvaluator);
        default:
            log.error("Unknown value type '" + v.getType() + "' while evaluating condition");
            return null;
        }
    }

    // Some specific evaluators

    // Integer evaluator
    private static final Evaluator intEvaluator = new Evaluator() {
        @Override
        public boolean evaluate(OperatorType op, ResolvedValue lValue, ResolvedValue rValue) {
            boolean unsigned = lValue.unsigned && rValue.unsigned;
            // FIXME: signed/unsigned comparison should be warned, but frequently seen in 'Parameter op value'

            long lval = (long) lValue.value;
            long rval = (long) rValue.value;

            if (unsigned) {
                switch (op) {
                case EQUALITY:
                    return (lval == rval);
                case INEQUALITY:
                    return (lval != rval);
                case LARGERTHAN:
                    return UnsignedLongs.compare(lval, rval) > 0;
                case LARGEROREQUALTHAN:
                    return UnsignedLongs.compare(lval, rval) >= 0;
                case SMALLERTHAN:
                    return UnsignedLongs.compare(lval, rval) < 0;
                case SMALLEROREQUALTHAN:
                    return UnsignedLongs.compare(lval, rval) <= 0;
                }
            } else {
                switch (op) {
                case EQUALITY:
                    return (lval == rval);
                case INEQUALITY:
                    return (lval != rval);
                case LARGERTHAN:
                    return (lval > rval);
                case LARGEROREQUALTHAN:
                    return (lval >= rval);
                case SMALLERTHAN:
                    return (lval < rval);
                case SMALLEROREQUALTHAN:
                    return (lval <= rval);
                }
            }

            return false;
        }

        @Override
        public String getComparedType() {
            return "Integer";
        }
    };

    // Float evaluator
    private static final Evaluator floatEvaluator = new Evaluator() {
        @Override
        public boolean evaluate(OperatorType op, ResolvedValue lValue, ResolvedValue rValue) {
            double lval = (double) lValue.value;
            double rval = (double) rValue.value;

            switch (op) {
            case EQUALITY:
                return (lval == rval);
            case INEQUALITY:
                return (lval != rval);
            case LARGERTHAN:
                return (lval > rval);
            case LARGEROREQUALTHAN:
                return (lval >= rval);
            case SMALLERTHAN:
                return (lval < rval);
            case SMALLEROREQUALTHAN:
                return (lval <= rval);
            }
            return false;
        }

        @Override
        public String getComparedType() {
            return "Float";
        }
    };

    // String evaluator
    private static final Evaluator stringEvaluator = new Evaluator() {
        @Override
        public boolean evaluate(OperatorType op, ResolvedValue lValue, ResolvedValue rValue) {
            String lval = (String) lValue.value;
            String rval = (String) rValue.value;

            switch (op) {
            case EQUALITY:
                return (lval.compareTo(rval) == 0);
            case INEQUALITY:
                return (lval.compareTo(rval) != 0);
            case LARGERTHAN:
                return (lval.compareTo(rval) > 0);
            case LARGEROREQUALTHAN:
                return (lval.compareTo(rval) >= 0);
            case SMALLERTHAN:
                return (lval.compareTo(rval) < 0);
            case SMALLEROREQUALTHAN:
                return (lval.compareTo(rval) <= 0);
            }

            return false;
        }

        @Override
        public String getComparedType() {
            return "String";
        }

    };

    // Binary evaluator
    private static final Evaluator binaryEvaluator = new Evaluator() {
        @Override
        public boolean evaluate(OperatorType op, ResolvedValue lValue, ResolvedValue rValue) {
            byte[] lval = (byte[]) lValue.value;
            byte[] rval = (byte[]) rValue.value;

            Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

            switch (op) {
            case EQUALITY:
                return (comparator.compare(lval, rval) == 0);
            case INEQUALITY:
                return (comparator.compare(lval, rval) != 0);
            case LARGERTHAN:
                return (comparator.compare(lval, rval) > 0);
            case LARGEROREQUALTHAN:
                return (comparator.compare(lval, rval) >= 0);
            case SMALLERTHAN:
                return (comparator.compare(lval, rval) < 0);
            case SMALLEROREQUALTHAN:
                return (comparator.compare(lval, rval) <= 0);
            }

            return false;
        }

        @Override
        public String getComparedType() {
            return "Binary";
        }
    };

    // Boolean evaluator
    private static final Evaluator booleanEvaluator = new Evaluator() {
        @Override
        public boolean evaluate(OperatorType op, ResolvedValue lValue, ResolvedValue rValue) {
            boolean lval = (boolean) lValue.value;
            boolean rval = (boolean) rValue.value;

            switch (op) {
            case EQUALITY:
                return lval == rval;
            case INEQUALITY:
                return lval != rval;
            case LARGERTHAN:
                return Boolean.compare(lval, rval) > 0;
            case LARGEROREQUALTHAN:
                return Boolean.compare(lval, rval) >= 0;
            case SMALLERTHAN:
                return Boolean.compare(lval, rval) < 0;
            case SMALLEROREQUALTHAN:
                return Boolean.compare(lval, rval) <= 0;
            }
            return false;
        }

        @Override
        public String getComparedType() {
            return "Boolean";
        }
    };

    public static MatchCriteriaEvaluator ALWAYS_MATCH = new MatchCriteriaEvaluator() {

        @Override
        public MatchResult evaluate(ProcessingData input) {
            return MatchResult.OK;
        }

        @Override
        public String toExpressionString() {
            return "true";
        }
    };
}

class ResolvedValue {
    Object value;
    boolean unsigned;
    Evaluator evaluator;

    public ResolvedValue(Object value, boolean unsigned, Evaluator evaluator) {
        super();
        this.value = value;
        this.unsigned = unsigned;
        this.evaluator = evaluator;
    }

    @Override
    public String toString() {
        return "Value(" + value + ") Signed(" + unsigned + ") "
                + "EvaluatorType(" + evaluator.getComparedType() + ")";
    }
}

interface Evaluator {
    String getComparedType();

    boolean evaluate(OperatorType op, ResolvedValue lValue, ResolvedValue rValue);
}
