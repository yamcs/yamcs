package org.yamcs.mdb;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.BinaryValue;
import org.yamcs.parameter.BooleanValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.UnsignedLong;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.AbsoluteTimeParameterType;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.ArrayParameterType;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanDataType;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.FloatValidRange;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.IntegerValidRange;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.ReferenceTime;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtce.TimeEpoch;
import org.yamcs.xtce.TimeEpoch.CommonEpochs;

/**
 * Responsible for converting between raw and engineering value by usage of calibrators or by simple type conversions.
 * 
 */
public class ParameterTypeProcessor {
    ProcessorData pdata;
    static Logger log = LoggerFactory.getLogger(ParameterTypeProcessor.class.getName());

    boolean checkValidityRanges;

    public ParameterTypeProcessor(ProcessorData pdata) {
        this.pdata = pdata;
        checkValidityRanges = pdata.getProcessorConfig().checkParameterValidityRanges();
    }

    /**
     * Sets the value of a pval, based on the raw value, the applicable calibrator and the expected parameter type
     * <p>
     * Also checks the validity if a ValidRange is defined for the parameter type
     * 
     * @param pval
     */
    public void calibrate(ProcessingContext processingCtx, ParameterValue pval) {
        doCalibrate(processingCtx, pval);
    }

    public void calibrate(ParameterValue pval) {
        doCalibrate(null, pval);
    }

    /**
     * Checks validity if a ValidRange is defined for the parameter type
     */
    public void checkValidity(ParameterValue pval) {
        if (checkValidityRanges) {
            ParameterType ptype = pdata.getParameterType(pval.getParameter());
            doCheckValidity(ptype, pval);
        }
    }

    private void doCalibrate(ProcessingContext processingCtx, ParameterValue pval) {
        ParameterType ptype = pdata.getParameterType(pval.getParameter());
        Value rawValue = pval.getRawValue();
        Value engValue;
        try {
            if (requireCalibration(ptype, rawValue)) {
                engValue = doCalibrate(processingCtx, ptype, rawValue);
            } else {
                engValue = pval.getRawValue();
            }
            pval.setEngValue(engValue);
            if (checkValidityRanges) {
                doCheckValidity(ptype, pval);
            }
        } catch (XtceProcessingException e) {
            log.info("Exception calibrating {}: {}", pval, e);
            pval.setInvalid();
        } catch (Exception e) {
            log.error("Exception calibrating {}: {}" + pval, e);
            pval.setInvalid();
        }
    }

    private Value doCalibrate(ProcessingContext processingCtx, ParameterType ptype,
            Value rawValue) {
        Value engValue;

        if (ptype instanceof BaseDataType bdt) {
            CalibratorProc calibrator = pdata.getCalibrator(processingCtx, bdt);
            if (calibrator == null) {
                if (ptype instanceof EnumeratedParameterType ept) {
                    engValue = convertToEnumerated(ept, rawValue);
                } else if (ptype instanceof IntegerParameterType ipt) {
                    engValue = convertToInteger(processingCtx, ipt, rawValue);
                } else if (ptype instanceof FloatParameterType fpt) {
                    engValue = convertToFloat(processingCtx, fpt, rawValue);
                } else if (ptype instanceof BinaryParameterType bpt) {
                    engValue = convertToBinary(bpt, rawValue);
                } else if (ptype instanceof StringParameterType spt) {
                    engValue = convertToString(spt, rawValue);
                } else if (ptype instanceof BooleanParameterType bpt) {
                    engValue = convertToBoolean(bpt, rawValue);
                } else if (ptype instanceof AbsoluteTimeParameterType apt) {
                    engValue = calibrateAbsoluteTime(processingCtx, apt, rawValue);
                } else {
                    throw new IllegalStateException("Extraction of " + ptype + " not implemented");
                }
            } else {
                engValue = calibrator.calibrate(rawValue, processingCtx);
            }
        } else if (ptype instanceof AggregateParameterType apt) {
            engValue = calibrateAggregate(processingCtx, apt, (AggregateValue) rawValue);
        } else if (ptype instanceof ArrayParameterType apt) {
            engValue = calibrateArray(processingCtx, apt, (ArrayValue) rawValue);
        } else {
            throw new IllegalStateException("Extraction of " + ptype + " not implemented");
        }
        return engValue;
    }

    private boolean requireCalibration(ParameterType ptype, Value rawValue) {
        if ((ptype instanceof BaseDataType bdtype) && hasCalibrator(bdtype)) {
            return true;
        } else if (rawValue instanceof AggregateValue aggrv) {
            if (ptype instanceof AggregateParameterType aggptype) {
                for (Member m : aggptype.getMemberList()) {
                    var v = aggrv.getMemberValue(m.getName());
                    if (v == null) {
                        return true;
                    }
                    if (requireCalibration((ParameterType) m.getType(), v)) {
                        return true;
                    }
                }
                return false;
            } else {
                return true;
            }
        } else if (rawValue instanceof ArrayValue arrv) {
            if (ptype instanceof ArrayParameterType aptype) {
                if (arrv.isEmpty()) {
                    return aptype.getElementType().getValueType() != arrv.getElementType();
                } else {
                    // we assume here that all the elements of the array are of the same type
                    return requireCalibration((ParameterType) aptype.getElementType(), arrv.getElementValue(0));
                }
            } else {
                return true;
            }
        } else {
            return rawValue.getType() != ptype.getValueType();
        }
    }

    private boolean hasCalibrator(BaseDataType bpt) {
        DataEncoding encoding = bpt.getEncoding();
        if (encoding == null) {
            return false;
        }
        return encoding.getContextCalibratorList() != null || encoding.getDefaultCalibrator() != null;
    }

    private static Value convertToEnumerated(EnumeratedParameterType ept, Value rawValue) {
        switch (rawValue.getType()) {
        case UINT32:
            return ValueUtility.getEnumeratedValue(rawValue.getUint32Value(), ept.calibrate(rawValue.getUint32Value()));
        case UINT64:
            return ValueUtility.getEnumeratedValue(rawValue.getUint64Value(), ept.calibrate(rawValue.getUint64Value()));
        case SINT32:
            return ValueUtility.getEnumeratedValue(rawValue.getSint32Value(), ept.calibrate(rawValue.getSint32Value()));
        case SINT64:
            return ValueUtility.getEnumeratedValue(rawValue.getSint64Value(), ept.calibrate(rawValue.getSint64Value()));
        case FLOAT:
            return ValueUtility.getEnumeratedValue((long) rawValue.getFloatValue(),
                    ept.calibrate((long) rawValue.getFloatValue()));
        case DOUBLE:
            return ValueUtility.getEnumeratedValue((long) rawValue.getDoubleValue(),
                    ept.calibrate((long) rawValue.getDoubleValue()));
        case STRING:
            try {
                long l = Long.decode(rawValue.getStringValue());
                return ValueUtility.getEnumeratedValue(l, ept.calibrate(l));
            } catch (NumberFormatException e) {
                throw new XtceProcessingException(String.format("%s: failed to parse string '%s' to long",
                        ept.getName(), rawValue.getStringValue()));
            }
        case BINARY:
            byte[] b = rawValue.getBinaryValue();
            long l = binaryToLong(b);
            return ValueUtility.getEnumeratedValue(l, ept.calibrate(l));
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
        if (b.length < 8) {
            b1 = new byte[8];
            System.arraycopy(b, 0, b1, 8 - b.length, b.length);
        }
        return ByteArrayUtils.decodeLong(b1, 0);
    }

    private static Value convertToBoolean(BooleanParameterType bpt, Value rawValue) {
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
            return stringToBool(bpt, rawValue.getStringValue());
        case BOOLEAN:
            return rawValue;
        case BINARY:
            ByteBuffer buf = ByteBuffer.wrap(rawValue.getBinaryValue());
            boolean b = true;
            while (buf.hasRemaining()) {
                if (buf.get() != 0x00) {
                    b = false;
                    break;
                }
            }
            return ValueUtility.getBooleanValue(b);
        default:
            throw new IllegalStateException(
                    "Unsupported raw value type '" + rawValue.getType() + "' cannot be calibrated as a boolean");
        }
    }

    private static Value stringToBool(BooleanDataType bdt, String rawValue) {
        if (rawValue.isEmpty() || rawValue.equalsIgnoreCase(bdt.getZeroStringValue()) || rawValue.equals("0")) {
            return BooleanValue.FALSE;
        } else {
            return BooleanValue.TRUE;
        }
    }

    private static Value convertToBinary(BinaryParameterType bpt, Value rawValue) {
        switch (rawValue.getType()) {
        case SINT32:
            return new BinaryValue(BigInteger.valueOf(rawValue.getSint32Value()).toByteArray());
        case UINT32:
            return new BinaryValue(BigInteger.valueOf(rawValue.getUint32Value() & 0xFFFF_FFFFL).toByteArray());
        case SINT64:
            return new BinaryValue(BigInteger.valueOf(rawValue.getSint64Value()).toByteArray());
        case UINT64:
            return new BinaryValue(BigInteger.valueOf(rawValue.getUint64Value()).toByteArray());
        default:
            return rawValue;
        }
    }

    private Value convertToInteger(ProcessingContext processingCtx, IntegerParameterType ipt, Value rawValue) {
        switch (rawValue.getType()) {
        case SINT32:
            return convertToInterger(processingCtx, ipt, rawValue.getSint32Value());
        case SINT64:
            return convertToInterger(processingCtx, ipt, rawValue.getSint64Value());
        case UINT32:
            return convertToInterger(processingCtx, ipt, rawValue.getUint32Value() & 0xFFFFFFFFL);
        case UINT64:
            return convertToInterger(processingCtx, ipt, rawValue.getUint64Value());
        case FLOAT:
            return convertToInterger(processingCtx, ipt, (long) rawValue.getFloatValue());
        case DOUBLE:
            return convertToInterger(processingCtx, ipt, (long) rawValue.getDoubleValue());
        case STRING:
            try {
                long l = Long.decode(rawValue.getStringValue());
                return convertToInterger(processingCtx, ipt, l);
            } catch (NumberFormatException e) {
                try {
                    long l = (long) Double.parseDouble(rawValue.getStringValue());
                    return convertToInterger(processingCtx, ipt, l);
                } catch (NumberFormatException e2) {
                    throw new XtceProcessingException(String.format("%s: failed to parse string '%s' to long",
                            ipt.getName(), rawValue.getStringValue()));
                }
            }
        default:
            throw new IllegalStateException(
                    "Unsupported raw value type '" + rawValue.getType() + "' cannot be converted to integer");
        }
    }

    private Value convertToInterger(ProcessingContext processingCtx, IntegerParameterType ipt, long longValue) {
        if (ipt.getSizeInBits() <= 32) {
            if (ipt.isSigned()) {
                return ValueUtility.getSint32Value((int) longValue);
            } else {
                return ValueUtility.getUint32Value((int) longValue);
            }
        } else {
            if (ipt.isSigned()) {
                return ValueUtility.getSint64Value(longValue);
            } else {
                return ValueUtility.getUint64Value(longValue);
            }
        }
    }

    private static Value convertToString(StringParameterType spt, Value rawValue) {
        if (rawValue.getType() == Type.STRING) {
            return rawValue;
        } else {
            throw new IllegalStateException(
                    "Unsupported raw value type '" + rawValue.getType() + "' cannot be converted to string");
        }
    }

    private Value convertToFloat(ProcessingContext processingCtx, FloatParameterType ptype, Value rawValue) {
        switch (rawValue.getType()) {
        case DOUBLE:
            return convertToFloat(processingCtx, ptype, rawValue.getDoubleValue());
        case FLOAT:
            return convertToFloat(processingCtx, ptype, rawValue.getFloatValue());
        case SINT32:
            return convertToFloat(processingCtx, ptype, rawValue.getSint32Value());
        case SINT64:
            return convertToFloat(processingCtx, ptype, rawValue.getSint64Value());
        case UINT32:
            return convertToFloat(processingCtx, ptype, rawValue.getUint32Value() & 0xFFFFFFFFL);
        case UINT64:
            return convertToFloat(processingCtx, ptype, UnsignedLong.toDouble(rawValue.getUint64Value()));
        case STRING:
            try {
                Double d = Double.parseDouble(rawValue.getStringValue());
                return convertToFloat(processingCtx, ptype, d);
            } catch (NumberFormatException e) {
                throw new XtceProcessingException(String.format("%s: failed to parse string '%s' to double",
                        ptype.getName(), rawValue.getStringValue()));
            }
        default:
            throw new IllegalStateException(
                    "Unsupported raw value type '" + rawValue.getType() + "' cannot be converted to float");
        }
    }

    private Value convertToFloat(ProcessingContext processingCtx, FloatParameterType ptype,
            double doubleValue) {

        if (ptype.getSizeInBits() == 32) {
            return ValueUtility.getFloatValue((float) doubleValue);
        } else {
            return ValueUtility.getDoubleValue(doubleValue);
        }
    }

    private Value calibrateAbsoluteTime(ProcessingContext processingCtx, AbsoluteTimeParameterType ptype,
            Value rawValue) {

        ReferenceTime rtime = ptype.getReferenceTime();
        if (rtime == null) {
            if (rawValue.getType() == Type.TIMESTAMP) {
                return rawValue;
            } else {
                throw new XtceProcessingException(
                        "Raw value type '" + rawValue.getType()
                                + "' cannot be converted to absolute time without a reference time");
            }
        }

        TimeEpoch epoch = rtime.getEpoch();

        long offsetMillisec;
        switch (rawValue.getType()) {
        case SINT32:
            offsetMillisec = computeTime(ptype, rawValue.getSint32Value());
            break;
        case SINT64:
            offsetMillisec = computeTime(ptype, rawValue.getSint64Value());
            break;
        case UINT32:
            offsetMillisec = computeTime(ptype, rawValue.getUint32Value() & 0xFFFFFFFFL);
            break;
        case UINT64:
            offsetMillisec = computeTime(ptype, rawValue.getUint64Value());
            break;
        case FLOAT:
            offsetMillisec = computeTime(ptype, rawValue.getFloatValue());
            break;
        case DOUBLE:
            offsetMillisec = computeTime(ptype, rawValue.getDoubleValue());
            break;
        default:
            throw new IllegalStateException(
                    "Unsupported raw value type '" + rawValue.getType() + "' cannot be converted to absolute time");
        }
        long time = 0;

        if (epoch != null) {
            time = getEpochTime(epoch, offsetMillisec);
        } else {
            ParameterInstanceRef ref = rtime.getOffsetFrom();
            if (ref != null) {
                long referenceTime = getParaReferenceTime(processingCtx, ptype, ref);
                if (referenceTime == TimeEncoding.INVALID_INSTANT) {
                    throw new XtceProcessingException(ptype.getName() + ": could not extract the reference time");
                }
                time = offsetMillisec + referenceTime;
            } else {
                throw new XtceProcessingException(
                        ptype.getName() + ": cannot calibrate with a epoch without a reference");
            }
        }
        return ValueUtility.getTimestampValue(time);
    }

    private long computeTime(AbsoluteTimeParameterType ptype, long offset) {
        if (ptype.needsScaling()) {
            return (long) (1000 * ptype.getOffset() + 1000 * ptype.getScale() * offset);
        } else {
            return 1000 * offset;
        }
    }

    private long computeTime(AbsoluteTimeParameterType ptype, double offset) {
        if (ptype.needsScaling()) {
            return (long) (1000 * ptype.getOffset() + 1000 * ptype.getScale() * offset);
        } else {
            return (long) (1000 * offset);
        }
    }

    private long getParaReferenceTime(ProcessingContext processingCtx, ParameterType ptype,
            ParameterInstanceRef ref) {
        if (processingCtx == null) {
            log.warn("{}: no parameter processing context avaialble", ptype.getName());
            return TimeEncoding.INVALID_INSTANT;
        }
        ParameterValue pv = processingCtx.getParameterInstance(ref);
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

    static long getEpochTime(TimeEpoch epoch, long offset) {
        CommonEpochs ce = epoch.getCommonEpoch();
        if (ce != null) {
            switch (ce) {
            case GPS:
                return TimeEncoding.fromGpsMillisec(offset);
            case J2000:
                return TimeEncoding.fromJ2000Millisec(offset);
            case TAI:
                return TimeEncoding.fromTaiMillisec(offset);
            case UNIX:
                return TimeEncoding.fromUnixMillisec(offset);
            default:
                throw new IllegalStateException("Unknonw epoch " + ce);
            }
        } else {
            return offset + TimeEncoding.parse(epoch.getDateTime());
        }
    }

    private Value calibrateAggregate(ProcessingContext processingCtx, AggregateParameterType ptype,
            AggregateValue rawValue) {
        AggregateValue engValue = new AggregateValue(ptype.getMemberNames());
        for (Member m : ptype.getMemberList()) {
            Value rv = rawValue.getMemberValue(m.getName());
            Value ev = doCalibrate(processingCtx, (ParameterType) m.getType(), rv);
            engValue.setMemberValue(m.getName(), ev);
        }
        return engValue;
    }

    private Value calibrateArray(ProcessingContext processingCtx, ArrayParameterType ptype, ArrayValue rawValue) {
        ParameterType engElementType = (ParameterType) ptype.getElementType();

        int fl = rawValue.flatLength();
        Value rv = rawValue.getElementValue(0);
        Value ev = doCalibrate(processingCtx, engElementType, rv);
        ArrayValue engValue = new ArrayValue(rawValue.getDimensions(), ev.getType());
        engValue.setElementValue(0, ev);
        for (int i = 1; i < fl; i++) {
            rv = rawValue.getElementValue(i);
            ev = doCalibrate(processingCtx, engElementType, rv);
            engValue.setElementValue(i, ev);
        }
        return engValue;
    }

    private void doCheckValidity(ParameterType ptype, ParameterValue pval) {
        if (ptype instanceof FloatParameterType floatType) {
            FloatValidRange fvr = floatType.getValidRange();
            if (fvr != null) {
                Value v;
                if (fvr.isValidRangeAppliesToCalibrated()) {
                    v = pval.getEngValue();
                } else {
                    v = pval.getRawValue();
                }
                boolean valid = ValidRangeChecker.checkFloatRange(fvr, v);
                if (!valid) {
                    pval.setInvalid();
                }
            }
        } else if (ptype instanceof IntegerParameterType intType) {
            IntegerValidRange ivr = intType.getValidRange();
            if (ivr != null) {
                Value v;
                if (ivr.isValidRangeAppliesToCalibrated()) {
                    v = pval.getEngValue();
                } else {
                    v = pval.getRawValue();
                }
                boolean valid = ValidRangeChecker.checkIntegerRange(ivr, v);
                if (!valid) {
                    pval.setInvalid();
                }
            }
        }
    }
}
