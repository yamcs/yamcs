package org.yamcs.mdb;

import java.util.List;
import java.util.Map;

import org.yamcs.ErrorInCommand;
import org.yamcs.commanding.ArgumentValue;
import org.yamcs.logging.Log;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.EnumeratedValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.AbsoluteTimeArgumentType;
import org.yamcs.xtce.AggregateArgumentType;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentInstanceRef;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.ArrayArgumentType;
import org.yamcs.xtce.BinaryArgumentType;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.BooleanArgumentType;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.FixedIntegerValue;
import org.yamcs.xtce.FloatArgumentType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatValidRange;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerRange;
import org.yamcs.xtce.IntegerValidRange;
import org.yamcs.xtce.IntegerValue;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ReferenceTime;
import org.yamcs.xtce.StringArgumentType;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.TimeEpoch;
import org.yamcs.xtce.TimeEpoch.CommonEpochs;
import org.yamcs.xtce.ValueEnumeration;

/**
 * Handles conversions from engineering value to raw value according to the parameter type and encoding
 */
public class ArgumentTypeProcessor {
    final TcProcessingContext pcontext;

    final Log log;

    public ArgumentTypeProcessor(TcProcessingContext pcontext) {
        this.pcontext = pcontext;
        ProcessorData pdata = pcontext.pdata;

        log = new Log(this.getClass(), pdata.getYamcsInstance());
        log.setContext(pdata.getProcessorName());
    }

    public Value decalibrate(ArgumentType atype, Value v) {
        if (atype instanceof EnumeratedArgumentType) {
            return decalibrateEnumerated((EnumeratedArgumentType) atype, v);
        } else if (atype instanceof IntegerArgumentType) {
            return decalibrateInteger((IntegerArgumentType) atype, v);
        } else if (atype instanceof FloatArgumentType) {
            return decalibrateFloat((FloatArgumentType) atype, v);
        } else if (atype instanceof StringArgumentType) {
            return decalibrateString((StringArgumentType) atype, v);
        } else if (atype instanceof BinaryArgumentType) {
            return decalibrateBinary((BinaryArgumentType) atype, v);
        } else if (atype instanceof BooleanArgumentType) {
            return decalibrateBoolean((BooleanArgumentType) atype, v);
        } else if (atype instanceof AbsoluteTimeArgumentType) {
            return decalibrateAbsoluteTime((AbsoluteTimeArgumentType) atype, v);
        } else if (atype instanceof AggregateArgumentType) {
            return decalibrateAggregate((AggregateArgumentType) atype, (AggregateValue) v);
        } else if (atype instanceof ArrayArgumentType) {
            return decalibrateArray((ArrayArgumentType) atype, (ArrayValue) v);
        } else {
            throw new IllegalArgumentException("decalibration for " + atype + " not implemented");
        }
    }

    private Value decalibrateEnumerated(EnumeratedArgumentType atype, Value v) {
        if (v.getType() == Type.ENUMERATED) {
            return ValueUtility.getSint64Value(((EnumeratedValue) v).getSint64Value());
        } else if (v.getType() != Type.STRING) {
            throw new IllegalArgumentException(
                    "Enumerated decalibrations only available for enumerated values or strings");
        }

        return ValueUtility.getSint64Value(atype.decalibrate(v.getStringValue()));
    }

    private Value decalibrateInteger(IntegerArgumentType ipt, Value v) {
        DataEncoding encoding = ipt.getEncoding();

        if (encoding instanceof IntegerDataEncoding) {
            CalibratorProc calibrator = pcontext.pdata.getDecalibrator(encoding);
            if (calibrator == null) {
                return DataEncodingUtils.getRawIntegerValue((IntegerDataEncoding) encoding, v);
            } else {
                long calibValue = (long) calibrator.calibrate(v.toDouble());
                return DataEncodingUtils.getRawIntegerValue((IntegerDataEncoding) encoding, calibValue);
            }
        } else if (encoding instanceof FloatDataEncoding) {
            CalibratorProc calibrator = pcontext.pdata.getDecalibrator(encoding);
            if (calibrator == null) {
                return DataEncodingUtils.getRawFloatValue((FloatDataEncoding) encoding, v.toDouble());
            } else {
                double calibValue = calibrator.calibrate(v.toDouble());
                return DataEncodingUtils.getRawFloatValue((FloatDataEncoding) encoding, calibValue);
            }
        } else if (encoding instanceof StringDataEncoding) {
            return ValueUtility.getStringValue(v.toString());
        } else {
            throw new IllegalStateException(
                    "Cannot convert integer to '" + encoding);
        }
    }

    private Value decalibrateBoolean(BooleanArgumentType ipt, Value v) {
        if (v.getType() != Type.BOOLEAN) {
            throw new IllegalStateException(
                    "Unsupported value type '" + v.getType() + "' cannot be converted to boolean");
        }
        boolean boolv = v.getBooleanValue();

        DataEncoding encoding = ipt.getEncoding();
        if (encoding instanceof IntegerDataEncoding) {
            return ValueUtility.getUint32Value(boolv ? 1 : 0);
        } else if (encoding instanceof FloatDataEncoding) {
            return ValueUtility.getFloatValue(boolv ? 1 : 0);
        } else if (encoding instanceof StringDataEncoding) {
            return ValueUtility.getStringValue(boolv ? ipt.getOneStringValue() : ipt.getZeroStringValue());
        } else if (encoding instanceof BinaryDataEncoding) {
            return ValueUtility.getBinaryValue(new byte[] { (byte) (boolv ? 1 : 0) });
        } else {
            return v;
        }
    }

    private Value decalibrateFloat(FloatArgumentType fat, Value v) {
        DataEncoding encoding = fat.getEncoding();

        if (encoding instanceof IntegerDataEncoding) {
            CalibratorProc calibrator = pcontext.pdata.getDecalibrator(encoding);
            if (calibrator == null) {
                return DataEncodingUtils.getRawIntegerValue((IntegerDataEncoding) encoding, (long) v.toDouble());
            } else {
                long calibValue = Math.round(calibrator.calibrate(v.toDouble()));
                return DataEncodingUtils.getRawIntegerValue((IntegerDataEncoding) encoding, (long) calibValue);
            }
        } else if (encoding instanceof FloatDataEncoding) {
            CalibratorProc calibrator = pcontext.pdata.getDecalibrator(encoding);
            if (calibrator == null) {
                return DataEncodingUtils.getRawFloatValue((FloatDataEncoding) encoding, v.toDouble());
            } else {
                double calibValue = calibrator.calibrate(v.toDouble());
                return DataEncodingUtils.getRawFloatValue((FloatDataEncoding) encoding, calibValue);
            }
        } else if (encoding instanceof StringDataEncoding) {
            return ValueUtility.getStringValue(v.toString());
        } else {
            throw new IllegalStateException(
                    "Cannot convert integer to '" + encoding);
        }
    }

    private static Value decalibrateString(StringArgumentType sat, Value v) {
        DataEncoding encoding = sat.getEncoding();
        if (encoding instanceof StringDataEncoding) {
            return v;
        } else if (encoding instanceof BinaryDataEncoding) {
            return ValueUtility.getBinaryValue(v.getStringValue().getBytes());
        } else {
            throw new IllegalStateException(
                    "Unsupported value type '" + v.getType() + "' cannot be converted to " + encoding);
        }
    }

    private static Value decalibrateBinary(BinaryArgumentType bat, Value v) {
        DataEncoding encoding = bat.getEncoding();
        if (encoding instanceof BinaryDataEncoding) {
            return v;
        } else if (encoding instanceof StringDataEncoding) {
            return DataEncodingUtils.rawRawStringValue(v);
        } else {
            throw new IllegalStateException(
                    "Unsupported value type '" + v.getType() + "' cannot be converted to " + encoding);
        }
    }

    private Value decalibrateAbsoluteTime(AbsoluteTimeArgumentType atype, Value v) {
        if (v.getType() != Type.TIMESTAMP) {
            throw new CommandEncodingException(pcontext,
                    "Unsupported value type '" + v.getType() + "' cannot be converted to timestamp");
        }

        ReferenceTime rtime = atype.getReferenceTime();
        if (rtime == null) {
            if (atype.getEncoding() instanceof BinaryDataEncoding) {
                // we assume that the binary data encoding can convert the raw value directly
                return v;
            } else {
                throw new CommandEncodingException(pcontext,
                        "Cannot convert absolute time argument without a reference time");
            }
        }

        TimeEpoch epoch = rtime.getEpoch();
        long epochOffset = 0;

        if (epoch != null) {
            epochOffset = getEpochOffset(epoch, v.getTimestampValue());
        } else {
            throw new CommandEncodingException(pcontext, "Cannot convert absolute time argument without an epoch");
        }
        DataEncoding enc = atype.getEncoding();

        if (enc instanceof FloatDataEncoding) {
            return ValueUtility.getDoubleValue(scaleDouble(atype, epochOffset));
        } else if (enc instanceof IntegerDataEncoding) {
            return ValueUtility.getSint64Value(scaleInt(atype, epochOffset));
        } else {
            throw new CommandEncodingException(pcontext, "Cannot convert encode absolute time to " + enc + " encoding");
        }
    }

    static long getEpochOffset(TimeEpoch epoch, long time) {
        CommonEpochs ce = epoch.getCommonEpoch();

        if (ce != null) {
            switch (ce) {
            case GPS:
                return TimeEncoding.toGpsTimeMillisec(time);
            case J2000:
                return TimeEncoding.toJ2000Millisec(time);
            case TAI:
                return TimeEncoding.toTaiMillisec(time);
            case UNIX:
                return TimeEncoding.toUnixMillisec(time);
            default:
                throw new IllegalStateException("Unknonw epoch " + ce);
            }
        } else {
            return TimeEncoding.parse(epoch.getDateTime());
        }
    }

    private long scaleInt(AbsoluteTimeArgumentType atype, long time) {
        if (atype.needsScaling()) {
            return (long) ((time - 1000 * atype.getOffset()) / (1000 * atype.getScale()));
        } else {
            return time / 1000;
        }
    }

    private double scaleDouble(AbsoluteTimeArgumentType atype, long time) {
        if (atype.needsScaling()) {
            return ((time - 1000 * atype.getOffset()) / (1000 * atype.getScale()));
        } else {
            return time / 1000.0;
        }
    }

    private Value decalibrateAggregate(AggregateArgumentType atype, AggregateValue v) {
        AggregateValue rv = new AggregateValue(atype.getMemberNames());
        for (Member aggm : atype.getMemberList()) {
            Value mv = decalibrate((ArgumentType) aggm.getType(), v.getMemberValue(aggm.getName()));
            rv.setMemberValue(aggm.getName(), mv);
        }
        return rv;
    }

    private Value decalibrateArray(ArrayArgumentType atype, ArrayValue v) {
        int n = v.flatLength();
        if (n == 0) {
            return v;
        }
        Value rv0 = decalibrate((ArgumentType) atype.getElementType(), v.getElementValue(0));
        ArrayValue rv = new ArrayValue(v.getDimensions(), rv0.getType());
        rv.setElementValue(0, rv0);
        for (int i = 1; i < n; i++) {
            Value rvi = decalibrate((ArgumentType) atype.getElementType(), v.getElementValue(i));
            rv.setElementValue(i, rvi);
        }

        return rv;
    }

    public void checkRange(ArgumentType type, Object o) throws ErrorInCommand {
        if (type instanceof IntegerArgumentType) {
            IntegerArgumentType intType = (IntegerArgumentType) type;

            long l = (Long) o;
            IntegerValidRange vr = ((IntegerArgumentType) type).getValidRange();
            if (vr != null) {
                if (intType.isSigned() && !ValidRangeChecker.checkIntegerRange(vr, l)) {
                    throw new ErrorInCommand("Value " + l + " is not in the range " + vr);
                } else if (!intType.isSigned() && !ValidRangeChecker.checkUnsignedIntegerRange(vr, l)) {
                    throw new ErrorInCommand("Value " + l + " is not in the range " + vr);
                }
            }
        } else if (type instanceof FloatArgumentType) {
            double d = (Double) o;
            FloatValidRange vr = ((FloatArgumentType) type).getValidRange();
            if (vr != null) {
                if (!ValidRangeChecker.checkFloatRange(vr, d)) {
                    throw new ErrorInCommand("Value " + d + " is not in the range " + vr);
                }
            }
        } else if (type instanceof StringArgumentType) {
            String v = (String) o;
            IntegerRange r = ((StringArgumentType) type).getSizeRangeInCharacters();

            if (r != null) {
                int length = v.length();
                if (length < r.getMinInclusive()) {
                    throw new ErrorInCommand("Value " + v + " supplied for parameter of type " + type
                            + " does not satisfy minimum length of " + r.getMinInclusive());
                }
                if (length > r.getMaxInclusive()) {
                    throw new ErrorInCommand("Value " + v + " supplied for parameter of type " + type
                            + " does not satisfy maximum length of " + r.getMaxInclusive());
                }
            }

        } else if (type instanceof BinaryArgumentType) {
            byte[] b = (byte[]) o;
            IntegerRange r = ((BinaryArgumentType) type).getSizeRangeInBytes();

            if (r != null) {
                int length = b.length;
                if (length < r.getMinInclusive()) {
                    throw new ErrorInCommand(
                            "Value " + StringConverter.arrayToHexString(b) + " supplied for parameter of type " + type
                                    + " does not satisfy minimum length of " + r.getMinInclusive());
                }
                if (length > r.getMaxInclusive()) {
                    throw new ErrorInCommand(
                            "Value " + StringConverter.arrayToHexString(b) + " supplied for parameter of type " + type
                                    + " does not satisfy maximum length of " + r.getMaxInclusive());
                }
            }
        } else if (type instanceof EnumeratedArgumentType) {
            EnumeratedArgumentType enumType = (EnumeratedArgumentType) type;
            List<ValueEnumeration> vlist = enumType.getValueEnumerationList();
            boolean found = false;
            String v = (String) o;

            for (ValueEnumeration ve : vlist) {
                if (ve.getLabel().equals(v)) {
                    found = true;
                }
            }
            if (!found) {
                throw new ErrorInCommand("Value '" + v
                        + "' supplied for enumeration argument cannot be found in enumeration list " + vlist);
            }
        } else if (type instanceof AggregateArgumentType) {
            AggregateArgumentType atype = (AggregateArgumentType) type;
            Map<String, Object> mvalue = (Map<String, Object>) o;

            for (Member m : atype.getMemberList()) {

                if (!mvalue.containsKey(m.getName())) {
                    throw new ErrorInCommand("Value for aggregate argument '" + type.getName()
                            + "' does not contain a value for member " + m.getName());
                }
                checkRange((ArgumentType) m.getType(), mvalue.get(m.getName()));
            }
        } else if (type instanceof ArrayArgumentType) {
            ArrayArgumentType arrType = (ArrayArgumentType) type;
            checkArrayRange(arrType, arrType.getNumberOfDimensions() - 1, (Object[]) o);
        } else if (type instanceof BooleanArgumentType || type instanceof AbsoluteTimeArgumentType) {
            // nothing to check
        } else {
            throw new IllegalArgumentException("Cannot process values of type " + type);
        }
    }

    private void checkArrayRange(ArrayArgumentType type, int n, Object[] elements)
            throws ErrorInCommand {
        type.getNumberOfDimensions();
        IntegerValue iv = type.getDimension(n);
        if (iv instanceof FixedIntegerValue) {
            FixedIntegerValue fiv = (FixedIntegerValue) iv;
            if (elements.length != fiv.getValue()) {
                throw new ErrorInCommand(getSizeError(type, n, fiv.getValue(), elements.length));
            }
        } else {
            DynamicIntegerValue div = (DynamicIntegerValue) iv;
            if (div.getDynamicInstanceRef() instanceof ParameterInstanceRef) {
                throw new ErrorInCommand("Dynamic array size specified by parameters not supported "
                        + "(used in " + type.getName() + ")");
            }

            // the length of the array is specified dynamically using a reference to another argument
            ArgumentInstanceRef lengthAref = (ArgumentInstanceRef) div.getDynamicInstanceRef();
            if (!lengthAref.useCalibratedValue()) {
                // this cannot be supported for the moment because we do not have the raw values of arguments at
                // this point
                throw new ErrorInCommand("Dynamic argument array sizes specified using raw value not supported "
                        + "(used in " + type.getName() + ")");
            }

            ArgumentValue argValue = pcontext.getArgumentValue(lengthAref.getName());
            if (argValue != null) {
                // the command contains a value for the argument giving the length, verify that it matches the length of
                // the array
                long expectedSize = div.transform(argValue.getEngValue().toLong());
                if (elements.length != expectedSize) {
                    throw new ErrorInCommand(getSizeError(type, n, expectedSize, elements.length));
                }
            } else {
                // the argument specifying the length is not present, compute it automatically based on the length of
                // the array
                Argument lengthArg = pcontext.getArgument(lengthAref.getName());
                if (lengthArg == null) {
                    throw new ErrorInCommand("length of array " + type.getName()
                            + " makes reference to non-existent argument '" + lengthAref.getName()
                            + "' for command " + pcontext.getCommand());
                }
                long lengthArgValue = div.reverse(elements.length);
                checkRange(lengthArg.getArgumentType(), lengthArgValue);
                Value v = DataTypeProcessor.getValueForType(lengthArg.getArgumentType(), Long.valueOf(lengthArgValue));
                pcontext.addArgumentValue(lengthArg, v);
            }
        }

        if (n == 0) {
            for (Object elementValue : elements) {
                checkRange((ArgumentType) type.getElementType(), elementValue);
            }
        } else {
            for (Object elementValue : elements) {
                checkArrayRange(type, n - 1, (Object[]) elementValue);
            }
        }
    }

    private static String getSizeError(ArrayArgumentType type, int dim, long expected, long actual) {
        StringBuilder sb = new StringBuilder();
        sb.append("bad size for ");
        if (type.getNumberOfDimensions() > 1) {
            sb.append(dim + 1);
            sb.append("th dimension of");
        }
        sb.append(" array of type ")
                .append(type.getName())
                .append("; expected size: ")
                .append(expected)
                .append(", actual size: ").append(actual);
        return sb.toString();
    }
}
