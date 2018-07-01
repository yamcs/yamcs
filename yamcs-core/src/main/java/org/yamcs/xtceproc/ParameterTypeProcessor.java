package org.yamcs.xtceproc;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.UnsignedLong;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.AbsoluteTimeParameterType;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.ArrayParameterType;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.CriteriaEvaluator;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.NumericDataEncoding;
import org.yamcs.xtce.NumericParameterType;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.ReferenceTime;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtce.TimeEpoch;
import org.yamcs.xtce.TimeEpoch.CommonEpochs;


/**
 * Responsible for converting between raw and engineering value by usage of calibrators or by simple type conversions.
 * 
 * 
 * @author nm
 *
 */
public class ParameterTypeProcessor {
    ProcessorData pdata;
    static Logger log = LoggerFactory.getLogger(ParameterTypeProcessor.class.getName());

    public ParameterTypeProcessor(ProcessorData pdata) {
        this.pdata = pdata;
    }

    /**
     * Sets the value of a pval, based on the raw value, the applicable calibrator and the expected parameter type
     * 
     * @param pval
     */
    public void calibrate(ContainerProcessingContext pcontext, ParameterValue pval) {
        Value engValue = doCalibrate(pcontext.result.params, pcontext.criteriaEvaluator, pval.getParameter().getParameterType(), pval.getRawValue());
        if(engValue!=null) {
            pval.setEngineeringValue(engValue);
        } else {
            pval.setAcquisitionStatus(AcquisitionStatus.INVALID);
        }
    }

    public void calibrate(ParameterValue pval) {
        Value engValue = doCalibrate(null, null, pval.getParameter().getParameterType(), pval.getRawValue());
        if(engValue!=null) {
            pval.setEngineeringValue(engValue);
        } else {
            pval.setAcquisitionStatus(AcquisitionStatus.INVALID);
        }
    }

    private Value doCalibrate(ParameterValueList pvalues, CriteriaEvaluator contextEvaluator, ParameterType ptype, Value rawValue) {
        Value engValue;

        if (ptype instanceof EnumeratedParameterType) {
            engValue = calibrateEnumerated((EnumeratedParameterType) ptype, rawValue);
        } else if (ptype instanceof IntegerParameterType) {
            engValue = calibrateInteger(contextEvaluator, (IntegerParameterType) ptype, rawValue);
        } else if (ptype instanceof FloatParameterType) {
            engValue = calibrateFloat(contextEvaluator, (FloatParameterType) ptype, rawValue);
        } else if (ptype instanceof BinaryParameterType) {
            engValue = calibrateBinary((BinaryParameterType) ptype, rawValue);
        } else if (ptype instanceof StringParameterType) {
            engValue = calibrateString((StringParameterType) ptype, rawValue);
        } else if (ptype instanceof BooleanParameterType) {
            engValue = calibrateBoolean((BooleanParameterType) ptype, rawValue);
        } else if (ptype instanceof AbsoluteTimeParameterType) {
            engValue = calibrateAbsoluteTime(pvalues, (AbsoluteTimeParameterType) ptype, rawValue);
        } else if (ptype instanceof AggregateParameterType) {
            engValue = calibrateAggregate(pvalues, contextEvaluator, (AggregateParameterType) ptype, (AggregateValue) rawValue);
        } else if (ptype instanceof ArrayParameterType) {
            engValue = calibrateArray(pvalues, contextEvaluator, (ArrayParameterType) ptype, (ArrayValue) rawValue);
        } else {
            throw new IllegalArgumentException("Extraction of " + ptype + " not implemented");
        }
        return engValue;
       
    }

    private static Value calibrateEnumerated(EnumeratedParameterType ept, Value rawValue) {
        switch (rawValue.getType()) {
        case UINT32:
            return ValueUtility.getStringValue(ept.calibrate(rawValue.getUint32Value()));
        case UINT64:
            return ValueUtility.getStringValue(ept.calibrate(rawValue.getUint64Value()));
        case SINT32:
            return ValueUtility.getStringValue(ept.calibrate(rawValue.getSint32Value()));
        case SINT64:
            return ValueUtility.getStringValue(ept.calibrate(rawValue.getSint64Value()));
        case FLOAT:
            return ValueUtility.getStringValue(ept.calibrate((long) rawValue.getFloatValue()));
        case DOUBLE:
            return ValueUtility.getStringValue(ept.calibrate((long) rawValue.getDoubleValue()));
        case STRING:
            try {
                long l = Long.decode(rawValue.getStringValue());
                return ValueUtility.getStringValue(ept.calibrate(l));
            } catch (NumberFormatException e) {
                log.warn("{}: failed to parse string '{}' to long", ept.getName(), rawValue.getStringValue());
                return null;
            }
        case BINARY:
            byte[] b = rawValue.getBinaryValue();
            long l = binaryToLong(b);
            return ValueUtility.getStringValue(ept.calibrate(l));
        default:
            throw new IllegalStateException(
                    "Unsupported raw value type '" + rawValue.getType() + "' cannot be calibrated as an enumeration");
        }
    }

    /*
     * encode the most significant 8 bytes of b to a long
     */
    private static long binaryToLong(byte[] b) {
        byte[] b1 = b;
        if(b.length<8) {
           b1 = new byte[8];
           System.arraycopy(b, 0, b1, 8-b.length, b.length);
        }
        return ByteArrayUtils.decodeLong(b1, 0);
    }

    private static Value calibrateBoolean(BooleanParameterType bpt, Value rawValue) {
        switch (rawValue.getType()) {
        case SINT32:
            return ValueUtility.getBooleanValue(rawValue.getSint32Value() != 0);
        case SINT64:
            return ValueUtility.getBooleanValue(rawValue.getSint64Value() != 0);
        case UINT32:
            return ValueUtility.getBooleanValue(rawValue.getUint32Value() != 0);
        case UINT64:
            return ValueUtility.getBooleanValue(rawValue.getUint64Value() != 0);
        case FLOAT:
            return ValueUtility.getBooleanValue(rawValue.getFloatValue() != 0);
        case DOUBLE:
            return ValueUtility.getBooleanValue(rawValue.getDoubleValue() != 0);
        case STRING:
            return ValueUtility
                    .getBooleanValue(rawValue.getStringValue() != null && !rawValue.getStringValue().isEmpty());
        case BOOLEAN:
            return rawValue;
        case BINARY:
            ByteBuffer buf = ByteBuffer.wrap(rawValue.getBinaryValue());
            boolean b = false;
            while (buf.hasRemaining()) {
                if (buf.get() != 0xFF) {
                    b = true;
                    break;
                }
            }
            return ValueUtility.getBooleanValue(b);
        default:
            throw new IllegalStateException(
                    "Unsupported raw value type '" + rawValue.getType() + "' cannot be calibrated as a boolean");
        }
    }

    private static Value calibrateBinary(BinaryParameterType bpt, Value rawValue) {
        return rawValue;
    }

    private Value calibrateInteger(CriteriaEvaluator contextEvaluator, IntegerParameterType ipt, Value rawValue) {
        if(!hasCalibrator(ipt) && ipt.getValueType() == rawValue.getType()) {
            return rawValue;
        }
        switch (rawValue.getType()) {
        case SINT32:
            return doIntegerCalibration(contextEvaluator, ipt, rawValue.getSint32Value());
        case SINT64:
            return doIntegerCalibration(contextEvaluator, ipt, rawValue.getSint64Value());
        case UINT32:
            return doIntegerCalibration(contextEvaluator, ipt, rawValue.getUint32Value() & 0xFFFFFFFFL);
        case UINT64:
            return doIntegerCalibration(contextEvaluator, ipt, rawValue.getUint64Value());
        case FLOAT:
            return doIntegerCalibration(contextEvaluator, ipt, (long) rawValue.getFloatValue());
        case DOUBLE:
            return doIntegerCalibration(contextEvaluator, ipt, (long) rawValue.getDoubleValue());
        case STRING:
            try {
                long l = Long.decode(rawValue.getStringValue());
                return doIntegerCalibration(contextEvaluator, ipt, l);
            } catch (NumberFormatException e) {
                log.warn("{}: failed to parse string '{}' to long", ipt.getName(), rawValue.getStringValue());
                return null;
            }
        default:
            throw new IllegalStateException(
                    "Unsupported raw value type '" + rawValue.getType() + "' cannot be converted to integer");
        }
    }

    private boolean hasCalibrator(NumericParameterType npt) {
        DataEncoding encoding = npt.getEncoding();
        if(encoding==null) {
            return false;
        }
        if(encoding instanceof NumericDataEncoding) {
            NumericDataEncoding nde = (NumericDataEncoding) encoding;
            return nde.getContextCalibratorList()!=null || nde.getDefaultCalibrator()!=null;
        } else {
            return false;
        }
    }

    private Value doIntegerCalibration(CriteriaEvaluator contextEvaluator, IntegerParameterType ipt, long longValue) {
        CalibratorProc calibrator = pdata.getCalibrator(contextEvaluator, ipt.getEncoding());

        long longCalValue = (calibrator == null) ? longValue : (long) calibrator.calibrate(longValue);

        if (ipt.getSizeInBits() <= 32) {
            if (ipt.isSigned())
                return ValueUtility.getSint32Value((int) longCalValue);
            else
                return ValueUtility.getUint32Value((int) longCalValue);
        } else {
            if (ipt.isSigned())
                return ValueUtility.getSint64Value(longCalValue);
            else
                return ValueUtility.getUint64Value(longCalValue);
        }
    }

    private static Value calibrateString(StringParameterType spt, Value rawValue) {
        if (rawValue.getType() == Type.STRING) {
            return rawValue;
        } else {
            throw new IllegalStateException(
                    "Unsupported raw value type '" + rawValue.getType() + "' cannot be converted to string");
        }
    }

    private Value calibrateFloat(CriteriaEvaluator contextEvaluator, FloatParameterType ptype, Value rawValue) {
        if(!hasCalibrator(ptype) && ptype.getValueType() == rawValue.getType()) {
            return rawValue;
        }
        switch (rawValue.getType()) {
        case DOUBLE:
            return doFloatCalibration(contextEvaluator, ptype, rawValue.getDoubleValue());
        case FLOAT:
            return doFloatCalibration(contextEvaluator, ptype, rawValue.getFloatValue());
        case SINT32:
            return doFloatCalibration(contextEvaluator, ptype, rawValue.getSint32Value());
        case SINT64:
            return doFloatCalibration(contextEvaluator, ptype, rawValue.getSint64Value());
        case UINT32:
            return doFloatCalibration(contextEvaluator, ptype, rawValue.getUint32Value() & 0xFFFFFFFFL);
        case UINT64:
            return doFloatCalibration(contextEvaluator, ptype, UnsignedLong.toDouble(rawValue.getUint64Value()));
        case STRING:
            try {
                Double d = Double.parseDouble(rawValue.getStringValue());
                return doFloatCalibration(contextEvaluator, ptype, d);
            } catch (NumberFormatException e) {
                log.warn("{}: failed to parse string '{}' to double", ptype.getName(), rawValue.getStringValue());
                return null;
            }
        default:
            throw new IllegalStateException(
                    "Unsupported raw value type '" + rawValue.getType() + "' cannot be converted to float");
        }
    }

    private Value doFloatCalibration(CriteriaEvaluator contextEvaluator, FloatParameterType ptype, double doubleValue) {
        CalibratorProc calibrator = pdata.getCalibrator(contextEvaluator, ptype.getEncoding());

        double doubleCalValue = (calibrator == null) ? doubleValue : calibrator.calibrate(doubleValue);
        if (ptype.getSizeInBits() == 32) {
            return ValueUtility.getFloatValue((float) doubleCalValue);
        } else {
            return ValueUtility.getDoubleValue(doubleCalValue);
        }
    }

    private Value calibrateAbsoluteTime(ParameterValueList context, AbsoluteTimeParameterType ptype, Value rawValue) {
        ReferenceTime rtime = ptype.getReferenceTime();
        TimeEpoch epoch = rtime.getEpoch();
        long referenceTime = 0;

        if (epoch != null) {
            referenceTime = getEpochTime(epoch);
        } else {
            ParameterInstanceRef ref = rtime.getOffsetFrom();
            if (ref != null) {
                referenceTime = getParaReferenceTime(context, ptype, ref);
                if (referenceTime == TimeEncoding.INVALID_INSTANT) {
                    return null;
                }
            } else {
                log.warn("{}: cannot calibrate with a epoch without a reference", ptype.getName());
                return null;
            }
        }
        long rt = referenceTime;
        switch (rawValue.getType()) {
        case SINT32:
            return ValueUtility.getTimestampValue(computeTime(ptype, rt, rawValue.getSint32Value()));
        case SINT64:
            return ValueUtility.getTimestampValue(computeTime(ptype, rt, rawValue.getSint64Value()));
        case UINT32:
            return ValueUtility.getTimestampValue(computeTime(ptype, rt, rawValue.getUint32Value() & 0xFFFFFFFFL));
        case UINT64:
            return ValueUtility.getTimestampValue(computeTime(ptype, rt, rawValue.getUint64Value()));
        case FLOAT:
            return ValueUtility.getTimestampValue(computeTime(ptype, rt, rawValue.getFloatValue()));
        case DOUBLE:
            return ValueUtility.getTimestampValue(computeTime(ptype, rt, rawValue.getDoubleValue()));
        default:
            throw new IllegalStateException(
                    "Unsupported raw value type '" + rawValue.getType() + "' cannot be converted to absolute time");
        }
    }

    private long computeTime(AbsoluteTimeParameterType ptype, long epochMillisec, long offset) {
        if (ptype.needsScaling()) {
            return (long) (epochMillisec + 1000 * ptype.getOffset() + 1000 * ptype.getScale() * offset);
        } else {
            return epochMillisec + 1000 * offset;
        }
    }

    private long computeTime(AbsoluteTimeParameterType ptype, long epochMillisec, double offset) {
        if (ptype.needsScaling()) {
            return (long) (epochMillisec + 1000 * ptype.getOffset() + 1000 * ptype.getScale() * offset);
        } else {
            return (long) (epochMillisec + 1000 * offset);
        }
    }

    private long getParaReferenceTime(ParameterValueList context, ParameterType ptype, ParameterInstanceRef ref) {
        if (context == null) {
            log.warn("{}: no parameter processing context avaialble", ptype.getName());
            return TimeEncoding.INVALID_INSTANT;
        }
        ParameterValue pv = context.getLastInserted(ref.getParameter());
        if (pv == null) {
            log.warn("{}: no instance of {} found in the processing context", ptype.getName(),
                    ref.getParameter().getQualifiedName());
            return TimeEncoding.INVALID_INSTANT;
        }
        Value v = pv.getEngValue();

        if (v.getType() != Type.TIMESTAMP) {
            log.warn("{}: instance {} is of type {} instead of required TIMESTAMP", ptype.getName(),
                    ref.getParameter().getQualifiedName(), v.getType());
            return TimeEncoding.INVALID_INSTANT;
        }
        return v.getTimestampValue();
    }

    private long getEpochTime(TimeEpoch epoch) {
        CommonEpochs ce = epoch.getCommonEpoch();

        if (ce != null) {
            switch (ce) {
            case GPS:
                return TimeEncoding.fromGpsMillisec(0);
            case J2000:
                return TimeEncoding.fromJ2000Millisec(0);
            case TAI:
                return TimeEncoding.fromTaiMillisec(0);
            case UNIX:
                return TimeEncoding.fromUnixMillisec(0);
            default:
                throw new IllegalStateException("Unknonw epoch " + ce);
            }
        } else {
            return TimeEncoding.parse(epoch.getDateTime());
        }
    }

    private Value calibrateAggregate(ParameterValueList pvalues, CriteriaEvaluator contextEvaluator, AggregateParameterType ptype, AggregateValue rawValue) {
        AggregateValue engValue = new AggregateValue(ptype.getMemberNames());
        for(Member m: ptype.getMemberList()) {
            Value rv = rawValue.getMemberValue(m.getName());
            if(rv!=null) {
                Value ev = doCalibrate(pvalues, contextEvaluator, (ParameterType) m.getType(), rv);
                if(ev!=null) {
                    engValue.setValue(m.getName(), ev);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
        return engValue;
    }
    
    private Value calibrateArray(ParameterValueList pvalues, CriteriaEvaluator contextEvaluator, ArrayParameterType ptype, ArrayValue rawValue) {
        ParameterType engValueType = (ParameterType) ptype.getElementType();
        boolean hasCalibrator = (engValueType instanceof NumericParameterType) && hasCalibrator((NumericParameterType) engValueType);
        if(!hasCalibrator && rawValue.getElementType() == engValueType.getValueType()) {
            return rawValue;
        } 
        int fl = rawValue.flatLength();
        Value rv = rawValue.getElementValue(0);
        Value ev = doCalibrate(pvalues, contextEvaluator, engValueType, rv);
        if(ev == null) {
            return null;
        }
        ArrayValue engValue = new ArrayValue(rawValue.getDimensions(), ev.getType());
        engValue.setElementValue(0, ev);
        for(int i = 1; i<fl; i++) {
            rv = rawValue.getElementValue(i);
            if(rv!=null) {
                ev = doCalibrate(pvalues, contextEvaluator, engValueType, rv);
                if(ev!=null) {
                    engValue.setElementValue(i, ev);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
        return engValue;
    }
}
