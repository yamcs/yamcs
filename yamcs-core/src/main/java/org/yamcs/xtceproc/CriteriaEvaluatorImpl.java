package org.yamcs.xtceproc;

import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.parameter.Value;
import org.yamcs.xtce.CriteriaEvaluator;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.PathElement;

import com.google.common.primitives.UnsignedBytes;
import com.google.common.primitives.UnsignedLongs;

public class CriteriaEvaluatorImpl implements CriteriaEvaluator {
    private static Logger LOG = LoggerFactory.getLogger(CriteriaEvaluatorImpl.class.getName());

    ParameterValueList currentDelivery;
    final LastValueCache lastValueCache;

    /**
     * 
     * @param currentDelivery
     *            - called in the context of XTCE packet processing - this contains the parameters just being extracted
     *            (they are not yet part of the lastValueCache)
     *            could be null if the evaluator is not running inside a packet processing
     * @param lastValueCache
     *            - contains the last know value of each parameter
     */
    public CriteriaEvaluatorImpl(ParameterValueList currentDelivery, LastValueCache lastValueCache) {
        this.currentDelivery = currentDelivery;
        this.lastValueCache = lastValueCache;
    }

    @Override
    public boolean evaluate(OperatorType op, Object lValueRef, Object rValueRef) {
        ResolvedValue lValue = resolveValue(lValueRef);
        ResolvedValue rValue = resolveValue(rValueRef);
        
        if ((lValue == null) || (rValue == null)) {
            return false;
        }

        if (lValue.evaluator == rValue.evaluator) {
            return lValue.evaluator.evaluate(op, lValue, rValue);
        } else {
            if ((lValue.evaluator == intEvaluator) && (lValue.evaluator == floatEvaluator)) {
                lValue.value = (double) ((long) lValue.value);
                return floatEvaluator.evaluate(op, lValue, rValue);
            } else if ((lValue.evaluator == floatEvaluator) && (lValue.evaluator == intEvaluator)) {
                rValue.value = (double) ((long) rValue.value);
                return floatEvaluator.evaluate(op, lValue, rValue);
            } else {
                LOG.error("Comparing values of incompatible types: "
                        + lValue.evaluator.getComparedType()
                        + " vs. " + rValue.evaluator.getComparedType());
            }
        }

        return false;
    }

    private ResolvedValue resolveValue(Object valueRef) {
        if (valueRef instanceof ParameterInstanceRef) {
            return resolveParameter((ParameterInstanceRef) valueRef);
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
            LOG.error("Unknown value type '" + valueRef.getClass() + "' while evaluating condition");
            return null;
        }
    }

    private ResolvedValue resolveParameter(ParameterInstanceRef paramRef) {
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
            if(path == null) {
                return null;
            }
            v = Value.getMemberValue(v, path);
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
            return new ResolvedValue(v.getStringValue(), false, stringEvaluator);
        case BINARY:
            return new ResolvedValue(v.getBinaryValue(), false, binaryEvaluator);
        case BOOLEAN:
            return new ResolvedValue(v.getBooleanValue(), false, booleanEvaluator);
        default:
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
