package org.yamcs.mdb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yamcs.ErrorInCommand;
import org.yamcs.commanding.ArgumentValue;
import org.yamcs.logging.Log;
import org.yamcs.mdb.TcProcessingContext.AggregateWithValue;
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
import org.yamcs.xtce.AggregateDataType;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentInstanceRef;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.ArrayArgumentType;
import org.yamcs.xtce.BaseDataType;
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
import org.yamcs.xtce.ValueEnumerationRange;

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

    /**
     * Performs the engineering value to the raw value conversion for the given type
     */
    public Value decalibrate(ArgumentType atype, Value engValue) {
        if (atype instanceof BaseDataType bdt) {
            DataEncoding encoding = bdt.getEncoding();
            if (encoding == null) {
                throw new XtceProcessingException("Conversion from engineering value is requested for argument type "
                        + atype.getName() + "; however there is no encoding defined");
            }
            CalibratorProc calibrator = pcontext.pdata.getDecalibrator(bdt);
            if (calibrator != null) {
                return calibrator.decalibrate(engValue, pcontext);
            }
            // if no calibrator is defined, we attempt to convert engValue to the rawValue based on the encoding
            if (atype instanceof EnumeratedArgumentType) {
                return decalibrateEnumerated((EnumeratedArgumentType) atype, engValue);
            } else if (atype instanceof IntegerArgumentType) {
                return decalibrateInteger((IntegerArgumentType) atype, engValue);
            } else if (atype instanceof FloatArgumentType) {
                return decalibrateFloat((FloatArgumentType) atype, engValue);
            } else if (atype instanceof StringArgumentType) {
                return decalibrateString((StringArgumentType) atype, engValue);
            } else if (atype instanceof BinaryArgumentType) {
                return decalibrateBinary((BinaryArgumentType) atype, engValue);
            } else if (atype instanceof BooleanArgumentType) {
                return decalibrateBoolean((BooleanArgumentType) atype, engValue);
            } else if (atype instanceof AbsoluteTimeArgumentType) {
                return decalibrateAbsoluteTime((AbsoluteTimeArgumentType) atype, engValue);
            } else {
                throw new IllegalStateException("Decalibration for type " + atype + " not implemented");
            }
        } else if (atype instanceof AggregateArgumentType) {
            return decalibrateAggregate((AggregateArgumentType) atype, (AggregateValue) engValue);
        } else if (atype instanceof ArrayArgumentType) {
            return decalibrateArray((ArrayArgumentType) atype, (ArrayValue) engValue);
        } else {
            throw new IllegalStateException("Decalibration for type " + atype + " not implemented");
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

        if (encoding instanceof IntegerDataEncoding ide) {
            return DataEncodingUtils.getRawIntegerValue(ide, v);
        } else if (encoding instanceof FloatDataEncoding fde) {
            return DataEncodingUtils.getRawFloatValue(fde, v.toDouble());
        } else if (encoding instanceof StringDataEncoding) {
            return ValueUtility.getStringValue(v.toString());
        } else {
            throw new XtceProcessingException("Cannot convert integer to '" + encoding);
        }
    }

    private Value decalibrateBoolean(BooleanArgumentType ipt, Value v) {
        if (v.getType() != Type.BOOLEAN) {
            throw new XtceProcessingException(
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

        if (encoding instanceof IntegerDataEncoding ide) {
            return DataEncodingUtils.getRawIntegerValue(ide, (long) v.toDouble());
        } else if (encoding instanceof FloatDataEncoding fde) {
            return DataEncodingUtils.getRawFloatValue(fde, v.toDouble());
        } else if (encoding instanceof StringDataEncoding) {
            return ValueUtility.getStringValue(v.toString());
        } else {
            throw new XtceProcessingException(
                    "Unsupported value type '" + v.getType() + "cannot be converted to '" + encoding);
        }
    }

    private static Value decalibrateString(StringArgumentType sat, Value v) {
        DataEncoding encoding = sat.getEncoding();
        if (encoding instanceof StringDataEncoding) {
            return v;
        } else if (encoding instanceof BinaryDataEncoding) {
            return ValueUtility.getBinaryValue(v.getStringValue().getBytes());
        } else {
            throw new XtceProcessingException(
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
            throw new XtceProcessingException(
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
            throw new CommandEncodingException(pcontext, "Cannot encode absolute time to " + enc + " encoding");
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
            return time - TimeEncoding.parse(epoch.getDateTime());
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

    public Value checkRangeAndConvertToValue(ArgumentType type, Object o) throws ErrorInCommand {
        if (type instanceof IntegerArgumentType intType) {
            Long l = (Long) o;
            IntegerValidRange vr = intType.getValidRange();
            if (vr != null) {
                if (intType.isSigned() && !ValidRangeChecker.checkIntegerRange(vr, l)) {
                    throw new ErrorInCommand("Value " + l + " is not in the range " + vr);
                } else if (!intType.isSigned() && !ValidRangeChecker.checkUnsignedIntegerRange(vr, l)) {
                    throw new ErrorInCommand("Value " + l + " is not in the range " + vr);
                }
            }
            if (intType.getSizeInBits() <= 32) {
                if (intType.isSigned()) {
                    return ValueUtility.getSint32Value(l.intValue());
                } else {
                    return ValueUtility.getUint32Value(l.intValue());
                }
            } else {
                if (intType.isSigned()) {
                    return ValueUtility.getSint64Value(l);
                } else {
                    return ValueUtility.getUint64Value(l);
                }
            }
        } else if (type instanceof FloatArgumentType floatType) {
            Double d = (Double) o;
            FloatValidRange vr = floatType.getValidRange();
            if (vr != null) {
                if (!ValidRangeChecker.checkFloatRange(vr, d)) {
                    throw new ErrorInCommand("Value " + d + " is not in the range " + vr);
                }
            }
            if (floatType.getSizeInBits() <= 32) {
                return ValueUtility.getFloatValue(d.floatValue());
            } else {
                return ValueUtility.getDoubleValue(d);
            }
        } else if (type instanceof StringArgumentType stringType) {
            String v = (String) o;
            IntegerRange r = stringType.getSizeRangeInCharacters();

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
            return ValueUtility.getStringValue(v);
        } else if (type instanceof BinaryArgumentType binaryType) {
            byte[] b = (byte[]) o;
            IntegerRange r = binaryType.getSizeRangeInBytes();

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
            return ValueUtility.getBinaryValue(b);
        } else if (type instanceof EnumeratedArgumentType enumType) {
            List<ValueEnumeration> vlist = enumType.getValueEnumerationList();
            String v = (String) o;
            ValueEnumeration ve = enumType.enumValue(v);
            if (ve != null) {
                return ValueUtility.getEnumeratedValue(ve.getValue(), ve.getLabel());
            }

            ValueEnumerationRange ver = enumType.enumValueRange(v);
            if (ver != null) {
                return ValueUtility.getEnumeratedValue((long) ver.getOneInRange(), ver.getLabel());
            }

            throw new ErrorInCommand("Value '" + v
                    + "' supplied for enumeration argument cannot be found in enumeration list " + vlist);

        } else if (type instanceof BooleanArgumentType) {
            return ValueUtility.getBooleanValue((Boolean) o);
        } else if (type instanceof AbsoluteTimeArgumentType) {
            return ValueUtility.getTimestampValue(TimeEncoding.parse((String) o));
        } else if (type instanceof AggregateArgumentType atype) {
            return chackAndGetAggregateValue((AggregateDataType) type, (Map<String, Object>) o);
        } else if (type instanceof ArrayArgumentType arrType) {
            return checkAndGetArrayValue(arrType, arrType.getNumberOfDimensions() - 1, (Object[]) o);
        } else {
            throw new IllegalArgumentException("Cannot process values of type " + type);
        }
    }

    private AggregateValue chackAndGetAggregateValue(AggregateDataType aggregateDataType,
            Map<String, Object> o) throws ErrorInCommand {
        pcontext.pushCurrentAggregate(new AggregateWithValue(aggregateDataType, o));

        AggregateValue v = new AggregateValue(aggregateDataType.getMemberNames());
        boolean updated, allFound;
        // when converting arrays, the length of the array may reference an aggregate member. In this case if the value
        // of the member is not set, it will be set when processing the array, deduced from the real array length.
        // this is why we have to try multiple times to resolve the member
        do {
            allFound = true;
            updated = false;
            for (Member m : aggregateDataType.getMemberList()) {
                if (v.getMemberValue(m.getName()) == null) {
                    Object o1 = o.get(m.getName());
                    if (o1 != null) {
                        v.setMemberValue(m.getName(), checkRangeAndConvertToValue((ArgumentType) m.getType(), o1));
                        updated = true;
                    } else {
                        allFound = false;
                    }
                }
            }
        } while (updated && !allFound);

        if (!allFound) {
            List<String> notFound = new ArrayList<>();
            for (Member m : aggregateDataType.getMemberList()) {
                if (v.getMemberValue(m.getName()) == null) {
                    notFound.add(m.getName());
                }
            }
            throw new IllegalArgumentException("No value provided for members " + notFound);
        }

        pcontext.popCurrentAggregate();

        if (v.numMembers() > 0) {
            return v;
        } else {
            return null;
        }
    }

    private ArrayValue checkAndGetArrayValue(ArrayArgumentType type, int numDimensions, Object[] elements)
            throws ErrorInCommand {
        if (type.getNumberOfDimensions() != 1) {
            throw new UnsupportedOperationException("TODO");
        }

        IntegerValue iv = type.getDimension(numDimensions);
        if (iv instanceof FixedIntegerValue) {
            FixedIntegerValue fiv = (FixedIntegerValue) iv;
            if (elements.length != fiv.getValue()) {
                throw new ErrorInCommand(getSizeError(type, numDimensions, fiv.getValue(), elements.length));
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

            String lengthRef = lengthAref.getName();

            // Since Yamcs 5.11.9: try to resolve the reference in the aggregate members (possibly traversing up to
            // the parents)
            AggregateWithValue awv = pcontext.getAggregateReference(lengthRef);
            if (awv != null) {
                // reference found, check if we have a value for it
                Object o = awv.value().get(lengthRef);
                if (o != null) {
                    // we have a value for it, check the correct type and that the value matches the array length
                    if (o instanceof Long l) {
                        long expectedSize = div.transform(l);
                        if (elements.length != expectedSize) {
                            throw new ErrorInCommand(getSizeError(type, numDimensions, expectedSize, elements.length));
                        }
                    } else {
                        throw new ErrorInCommand("Found a value for the array length reference '" + lengthRef
                                + "' but it is of type " + o.getClass() + " instead of Long: " + o);
                    }
                } else {
                    // there is no value for it, set it from the length of the array
                    // it may be there is another array referencing it, so this way when that one will be converted the
                    // code above will find the reference and check that the size of the other array is the same with
                    // this one
                    long lengthArgValue = div.reverse(elements.length);
                    awv.value().put(lengthAref.getName(), lengthArgValue);
                }
            } else {
                // reference could not be resolved in the aggregate members, try into the argument values
                ArgumentValue argValue = pcontext.getArgumentValue(lengthRef);

                if (argValue != null) {
                    // the command contains a value for the argument giving the length, verify that it matches the
                    // length of the array
                    long expectedSize = div.transform(argValue.getEngValue().toLong());
                    if (elements.length != expectedSize) {
                        throw new ErrorInCommand(getSizeError(type, numDimensions, expectedSize, elements.length));
                    }
                } else {
                    // the argument specifying the length is not present, compute it automatically based on the length
                    // of
                    // the array
                    Argument lengthArg = pcontext.getArgument(lengthAref.getName());
                    if (lengthArg == null) {
                        throw new ErrorInCommand("length of array " + type.getName()
                                + " makes reference to non-existent argument '" + lengthAref.getName()
                                + "' for command " + pcontext.getCommand());
                    }
                    long lengthArgValue = div.reverse(elements.length);
                    Value v = checkRangeAndConvertToValue(lengthArg.getArgumentType(), lengthArgValue);
                    pcontext.addArgumentValue(lengthArg, v);
                }
            }
        }

        ArrayValue v = new ArrayValue(new int[] { elements.length }, type.getElementType().getValueType());
        for (int i = 0; i < elements.length; i++) {
            v.setElementValue(i, checkRangeAndConvertToValue((ArgumentType) type.getElementType(), elements[i]));
        }

        return v;
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
