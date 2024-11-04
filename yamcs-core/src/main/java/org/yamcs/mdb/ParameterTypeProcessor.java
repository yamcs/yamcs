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
import org.yamcs.xtce.BooleanDataType;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.FloatValidRange;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.IntegerValidRange;
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
    public void calibrate(ProcessingData processingData, ParameterValue pval) {
        doCalibrate(processingData, pval);
    }

    public void calibrate(ParameterValue pval) {
        doCalibrate(null, pval);
    }

    private void doCalibrate(ProcessingData processingData, ParameterValue pval) {
        ParameterType ptype = pdata.getParameterType(pval.getParameter());
        Value engValue;

        if (requireCalibration(ptype, pval.getRawValue())) {
            engValue = doCalibrate(processingData, ptype, pval.getRawValue());
        } else {
            engValue = pval.getRawValue();
        }

        if (engValue != null) {
            pval.setEngValue(engValue);
            if (checkValidityRanges) {
                checkValidity(ptype, pval);
            }
        } else {
            pval.setAcquisitionStatus(AcquisitionStatus.INVALID);
        }
    }

    private boolean requireCalibration(ParameterType ptype, Value rawValue) {
        if (rawValue instanceof AggregateValue aggrv) {
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
        } else if ((ptype instanceof NumericParameterType nptype) && hasCalibrator(nptype)) {
            return true;
        } else {
            return rawValue.getType() != ptype.getValueType();
        }
    }

    private boolean hasCalibrator(NumericParameterType npt) {
        DataEncoding encoding = npt.getEncoding();
        if (encoding == null) {
            return false;
        }
        if (encoding instanceof NumericDataEncoding) {
            NumericDataEncoding nde = (NumericDataEncoding) encoding;
            return nde.getContextCalibratorList() != null || nde.getDefaultCalibrator() != null;
        } else {
            return false;
        }
    }

    private Value doCalibrate(ProcessingData processingData, ParameterType ptype,
            Value rawValue) {
        Value engValue;

        if (ptype instanceof EnumeratedParameterType) {
            engValue = calibrateEnumerated((EnumeratedParameterType) ptype, rawValue);
        } else if (ptype instanceof IntegerParameterType) {
            engValue = calibrateInteger(processingData, (IntegerParameterType) ptype, rawValue);
        } else if (ptype instanceof FloatParameterType) {
            engValue = calibrateFloat(processingData, (FloatParameterType) ptype, rawValue);
        } else if (ptype instanceof BinaryParameterType) {
            engValue = calibrateBinary((BinaryParameterType) ptype, rawValue);
        } else if (ptype instanceof StringParameterType) {
            engValue = calibrateString((StringParameterType) ptype, rawValue);
        } else if (ptype instanceof BooleanParameterType) {
            engValue = calibrateBoolean((BooleanParameterType) ptype, rawValue);
        } else if (ptype instanceof AbsoluteTimeParameterType) {
            engValue = calibrateAbsoluteTime(processingData, (AbsoluteTimeParameterType) ptype, rawValue);
        } else if (ptype instanceof AggregateParameterType) {
            engValue = calibrateAggregate(processingData, (AggregateParameterType) ptype,
                    (AggregateValue) rawValue);
        } else if (ptype instanceof ArrayParameterType) {
            engValue = calibrateArray(processingData, (ArrayParameterType) ptype, (ArrayValue) rawValue);
        } else {
            throw new IllegalArgumentException("Extraction of " + ptype + " not implemented");
        }
        return engValue;

    }

    private static Value calibrateEnumerated(EnumeratedParameterType ept, Value rawValue) {
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
                log.warn("{}: failed to parse string '{}' to long", ept.getName(), rawValue.getStringValue());
                return null;
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

    private static Value calibrateBinary(BinaryParameterType bpt, Value rawValue) {
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

    private Value calibrateInteger(ProcessingData processingData, IntegerParameterType ipt, Value rawValue) {

        switch (rawValue.getType()) {
        case SINT32:
            return doIntegerCalibration(processingData, ipt, rawValue.getSint32Value());
        case SINT64:
            return doIntegerCalibration(processingData, ipt, rawValue.getSint64Value());
        case UINT32:
            return doIntegerCalibration(processingData, ipt, rawValue.getUint32Value() & 0xFFFFFFFFL);
        case UINT64:
            return doIntegerCalibration(processingData, ipt, rawValue.getUint64Value());
        case FLOAT:
            return doIntegerCalibration(processingData, ipt, (long) rawValue.getFloatValue());
        case DOUBLE:
            return doIntegerCalibration(processingData, ipt, (long) rawValue.getDoubleValue());
        case STRING:
            try {
                long l = Long.decode(rawValue.getStringValue());
                return doIntegerCalibration(processingData, ipt, l);
            } catch (NumberFormatException e) {
                try {
                    long l = (long) Double.parseDouble(rawValue.getStringValue());
                    return doIntegerCalibration(processingData, ipt, l);
                } catch (NumberFormatException e2) {
                    log.warn("{}: failed to parse string '{}' to long", ipt.getName(), rawValue.getStringValue());
                    return null;
                }
            }
        default:
            throw new IllegalStateException(
                    "Unsupported raw value type '" + rawValue.getType() + "' cannot be converted to integer");
        }
    }


    private Value doIntegerCalibration(ProcessingData processingData, IntegerParameterType ipt, long longValue) {
        CalibratorProc calibrator = pdata.getCalibrator(processingData, ipt.getEncoding());

        long longCalValue = (calibrator == null) ? longValue : (long) calibrator.calibrate(longValue);

        if (ipt.getSizeInBits() <= 32) {
            if (ipt.isSigned()) {
                return ValueUtility.getSint32Value((int) longCalValue);
            } else {
                return ValueUtility.getUint32Value((int) longCalValue);
            }
        } else {
            if (ipt.isSigned()) {
                return ValueUtility.getSint64Value(longCalValue);
            } else {
                return ValueUtility.getUint64Value(longCalValue);
            }
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

    private Value calibrateFloat(ProcessingData processingData, FloatParameterType ptype, Value rawValue) {
        switch (rawValue.getType()) {
        case DOUBLE:
            return doFloatCalibration(processingData, ptype, rawValue.getDoubleValue());
        case FLOAT:
            return doFloatCalibration(processingData, ptype, rawValue.getFloatValue());
        case SINT32:
            return doFloatCalibration(processingData, ptype, rawValue.getSint32Value());
        case SINT64:
            return doFloatCalibration(processingData, ptype, rawValue.getSint64Value());
        case UINT32:
            return doFloatCalibration(processingData, ptype, rawValue.getUint32Value() & 0xFFFFFFFFL);
        case UINT64:
            return doFloatCalibration(processingData, ptype, UnsignedLong.toDouble(rawValue.getUint64Value()));
        case STRING:
            try {
                Double d = Double.parseDouble(rawValue.getStringValue());
                return doFloatCalibration(processingData, ptype, d);
            } catch (NumberFormatException e) {
                log.warn("{}: failed to parse string '{}' to double", ptype.getName(), rawValue.getStringValue());
                return null;
            }
        default:
            throw new IllegalStateException(
                    "Unsupported raw value type '" + rawValue.getType() + "' cannot be converted to float");
        }
    }

    private Value doFloatCalibration(ProcessingData processingData, FloatParameterType ptype,
            double doubleValue) {
        CalibratorProc calibrator = pdata.getCalibrator(processingData, ptype.getEncoding());

        double doubleCalValue = (calibrator == null) ? doubleValue : calibrator.calibrate(doubleValue);
        if (ptype.getSizeInBits() == 32) {
            return ValueUtility.getFloatValue((float) doubleCalValue);
        } else {
            return ValueUtility.getDoubleValue(doubleCalValue);
        }
    }

    private Value calibrateAbsoluteTime(ProcessingData processingData, AbsoluteTimeParameterType ptype,
            Value rawValue) {

        ReferenceTime rtime = ptype.getReferenceTime();
        if (rtime == null) {
            if (rawValue.getType() == Type.TIMESTAMP) {
                return rawValue;
            } else {
                throw new IllegalStateException(
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
                long referenceTime = getParaReferenceTime(processingData, ptype, ref);
                if (referenceTime == TimeEncoding.INVALID_INSTANT) {
                    return null;
                }
                time = offsetMillisec + referenceTime;
            } else {
                log.warn("{}: cannot calibrate with a epoch without a reference", ptype.getName());
                return null;
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

    private long getParaReferenceTime(ProcessingData processingData, ParameterType ptype,
            ParameterInstanceRef ref) {
        if (processingData == null) {
            log.warn("{}: no parameter processing context avaialble", ptype.getName());
            return TimeEncoding.INVALID_INSTANT;
        }
        ParameterValue pv = processingData.getParameterInstance(ref);
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

    private Value calibrateAggregate(ProcessingData processingData, AggregateParameterType ptype,
            AggregateValue rawValue) {
        AggregateValue engValue = new AggregateValue(ptype.getMemberNames());
        for (Member m : ptype.getMemberList()) {
            Value rv = rawValue.getMemberValue(m.getName());
            if (rv != null) {
                Value ev = doCalibrate(processingData, (ParameterType) m.getType(), rv);
                if (ev != null) {
                    engValue.setMemberValue(m.getName(), ev);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
        return engValue;
    }

    private Value calibrateArray(ProcessingData processingData, ArrayParameterType ptype, ArrayValue rawValue) {
        ParameterType engElementType = (ParameterType) ptype.getElementType();

        int fl = rawValue.flatLength();
        Value rv = rawValue.getElementValue(0);
        Value ev = doCalibrate(processingData, engElementType, rv);
        if (ev == null) {
            return null;
        }
        ArrayValue engValue = new ArrayValue(rawValue.getDimensions(), ev.getType());
        engValue.setElementValue(0, ev);
        for (int i = 1; i < fl; i++) {
            rv = rawValue.getElementValue(i);
            if (rv != null) {
                ev = doCalibrate(processingData, engElementType, rv);
                if (ev != null) {
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

    private void checkValidity(ParameterType ptype, ParameterValue pval) {
        if (ptype instanceof FloatParameterType) {
            FloatValidRange fvr = ((FloatParameterType) ptype).getValidRange();
            if (fvr != null) {
                Value v;
                if (fvr.isValidRangeAppliesToCalibrated()) {
                    v = pval.getEngValue();
                } else {
                    v = pval.getRawValue();
                }
                boolean valid = ValidRangeChecker.checkFloatRange(fvr, v);
                if (!valid) {
                    pval.setAcquisitionStatus(AcquisitionStatus.INVALID);
                }
            }
        } else if (ptype instanceof IntegerParameterType) {
            IntegerValidRange ivr = ((IntegerParameterType) ptype).getValidRange();
            if (ivr != null) {
                Value v;
                if (ivr.isValidRangeAppliesToCalibrated()) {
                    v = pval.getEngValue();
                } else {
                    v = pval.getRawValue();
                }
                boolean valid = ValidRangeChecker.checkIntegerRange(ivr, v);
                if (!valid) {
                    pval.setAcquisitionStatus(AcquisitionStatus.INVALID);
                }
            }
        }

    }

}
