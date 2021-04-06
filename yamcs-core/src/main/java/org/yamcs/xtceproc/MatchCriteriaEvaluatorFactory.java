package org.yamcs.xtceproc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.yamcs.logging.Log;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.parameter.Value;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.xtce.ANDedConditions;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.Condition;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.MatchCriteria.MatchResult;
import org.yamcs.xtce.util.DataTypeUtil;
import org.yamcs.xtce.ORedConditions;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.PathElement;

import com.google.common.primitives.UnsignedBytes;
import com.google.common.primitives.UnsignedLongs;

public abstract class MatchCriteriaEvaluatorFactory {

    public static final MatchCriteriaEvaluator getEvaluator(MatchCriteria matchCriteria) {
        if (matchCriteria instanceof Comparison) {
            return new ConditionEvaluator((Comparison) matchCriteria);
        } else if (matchCriteria instanceof ComparisonList) {
            return new ANDedConditionsEvaluator((ComparisonList) matchCriteria);
        } else if (matchCriteria instanceof Condition) {
            return new ConditionEvaluator((Condition) matchCriteria);
        } else if (matchCriteria instanceof ANDedConditions) {
            return new ANDedConditionsEvaluator((ANDedConditions) matchCriteria);
        } else if (matchCriteria instanceof ORedConditions) {
            return new ORedConditionsEvaluator((ORedConditions) matchCriteria);
        } else {
            throw new IllegalStateException("Unknown matchcriteria type " + matchCriteria.getClass());
        }
    }

    static class ConditionEvaluator implements MatchCriteriaEvaluator {
        private static Log log = new Log(ConditionEvaluator.class);
        ParameterInstanceRef lValueRef;
        Object rValueRef;
        final OperatorType comparisonOperator;

        public ConditionEvaluator(Condition condition) {
            this.comparisonOperator = condition.getComparisonOperator();
            rValueRef = condition.getrValueRef();
            lValueRef = condition.getlValueRef();

            String stringValue = condition.getStringValue();

            if (((rValueRef == null) || (!(rValueRef instanceof ParameterInstanceRef))) && (stringValue != null)) {
                ParameterType ptype = lValueRef.getParameter().getParameterType();
                if (lValueRef.useCalibratedValue()) {
                    rValueRef = ptype.parseString(stringValue);
                } else {
                    rValueRef = ptype.parseStringForRawValue(stringValue);
                }
            }
        }

        public ConditionEvaluator(Comparison comparison) {
            lValueRef = comparison.getParameterRef();

            boolean useCalibratedValue = lValueRef.useCalibratedValue();
            ParameterType ptype = lValueRef.getParameter().getParameterType();
            comparisonOperator = comparison.getComparisonOperator();

            if (ptype instanceof AggregateParameterType) {
                if (lValueRef.getMemberPath() == null) {
                    throw new IllegalArgumentException(
                            "Reference to an aggregate parameter type " + ptype.getName()
                                    + " without speciyfing the path");
                }
                ParameterType ptype1 = (ParameterType) DataTypeUtil.getMemberType(ptype, lValueRef.getMemberPath());
                if (ptype1 == null) {
                    throw new IllegalArgumentException(
                            "reference " + PathElement.pathToString(lValueRef.getMemberPath())
                                    + " points to a nonexistent member inside the parameter type " + ptype.getName());
                }
                ptype = ptype1;
            }
            try {
                if (useCalibratedValue) {
                    rValueRef = ptype.parseString(comparison.getStringValue());
                } else {
                    rValueRef = ptype.parseStringForRawValue(comparison.getStringValue());
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Cannot parse value required for comparing with "
                        + lValueRef.getParameter().getName() + ": " + e.getMessage(), e);
            }
        }

        @Override
        public MatchResult evaluate(ParameterValueList currentDelivery, LastValueCache lastValueCache) {
            ResolvedValue lValue = resolveValue(lValueRef, currentDelivery, lastValueCache);
            ResolvedValue rValue = resolveValue(rValueRef, currentDelivery, lastValueCache);

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

        private ResolvedValue resolveValue(Object valueRef,
                ParameterValueList currentDelivery, LastValueCache lastValueCache) {
            if (valueRef instanceof ParameterInstanceRef) {
                return resolveParameter((ParameterInstanceRef) valueRef, currentDelivery, lastValueCache);
            } else if (valueRef instanceof Integer) {
                return new ResolvedValue(((Integer) valueRef).longValue(), false, intEvaluator);
            } else if (valueRef instanceof Long) {
                return new ResolvedValue((Long) valueRef, false, intEvaluator);
            } else if (valueRef instanceof Float) {
                return new ResolvedValue(((Float) valueRef).doubleValue(), false, floatEvaluator);
            } else if (valueRef instanceof Double) {
                return new ResolvedValue((Double) valueRef, false, floatEvaluator);
            } else if (valueRef instanceof String) {
                return new ResolvedValue((String) valueRef, false, stringEvaluator);
            } else if (valueRef instanceof byte[]) {
                return new ResolvedValue((byte[]) valueRef, false, binaryEvaluator);
            } else if (valueRef instanceof Boolean) {
                return new ResolvedValue((Boolean) valueRef, false, booleanEvaluator);
            } else {
                log.error("Unknown value type '{}' while evaluating condition", valueRef);
                return null;
            }
        }

        private ResolvedValue resolveParameter(ParameterInstanceRef paramRef,
                ParameterValueList currentDelivery, LastValueCache lastValueCache) {
            ParameterValue pv = null;
            Parameter p = paramRef.getParameter();
            if (currentDelivery != null) {
                pv = currentDelivery.getLastInserted(p);
            }
            if (pv == null) {
                pv = lastValueCache.getValue(p);
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

        static interface Evaluator {
            String getComparedType();

            boolean evaluate(OperatorType op, ResolvedValue lValue, ResolvedValue rValue);
        }

        static class ResolvedValue {
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

        // Binary evaluator
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
        public MatchResult evaluate(ParameterValueList pvlist, LastValueCache lastValueCache) {
            MatchResult result = MatchResult.OK;

            for (MatchCriteriaEvaluator mce : evaluatorList) {
                MatchResult r = mce.evaluate(pvlist, lastValueCache);
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
        public MatchResult evaluate(ParameterValueList pvlist, LastValueCache lastValueCache) {
            MatchResult result = MatchResult.NOK;

            for (MatchCriteriaEvaluator mce : evaluatorList) {
                MatchResult r = mce.evaluate(pvlist, lastValueCache);
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
    }

    public static MatchCriteriaEvaluator ALWAYS_MATCH = new MatchCriteriaEvaluator() {

        @Override
        public MatchResult evaluate(ParameterValueList pvlist, LastValueCache lastValueCache) {
            return MatchResult.OK;
        }
    };

}
