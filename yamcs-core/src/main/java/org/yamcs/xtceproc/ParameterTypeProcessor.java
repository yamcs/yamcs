package org.yamcs.xtceproc;

import java.nio.ByteBuffer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.FloatValidRange;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.IntegerRange;
import org.yamcs.xtce.IntegerValidRange;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtce.ValueEnumeration;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.primitives.UnsignedLongs;

/**
 * Responsible for converting between raw and engineering value by usage of calibrators or by simple type conversions.
 * 
 * 
 * @author nm
 *
 */
public class ParameterTypeProcessor {
    ProcessorData pcontext;
    static Logger log=LoggerFactory.getLogger(ParameterTypeProcessor.class.getName());

    public ParameterTypeProcessor(ProcessorData pcontext) {
        this.pcontext = pcontext;
    }

    static Multimap<Class<? extends ParameterType>, org.yamcs.protobuf.Yamcs.Value.Type> allowedAssignments =
            new ImmutableSetMultimap.Builder<Class<? extends ParameterType>, org.yamcs.protobuf.Yamcs.Value.Type>()
            .putAll(BinaryParameterType.class, org.yamcs.protobuf.Yamcs.Value.Type.BINARY)
            .putAll(BooleanParameterType.class, org.yamcs.protobuf.Yamcs.Value.Type.BOOLEAN)
            .putAll(EnumeratedParameterType.class, org.yamcs.protobuf.Yamcs.Value.Type.STRING)
            .putAll(FloatParameterType.class, org.yamcs.protobuf.Yamcs.Value.Type.FLOAT, org.yamcs.protobuf.Yamcs.Value.Type.DOUBLE)
            .putAll(IntegerParameterType.class, org.yamcs.protobuf.Yamcs.Value.Type.UINT32, org.yamcs.protobuf.Yamcs.Value.Type.SINT32, org.yamcs.protobuf.Yamcs.Value.Type.SINT64, org.yamcs.protobuf.Yamcs.Value.Type.UINT64)
            .putAll(StringParameterType.class, org.yamcs.protobuf.Yamcs.Value.Type.STRING)
            .build();

    /**
     * Sets the value of a pval, based on the raw value and the applicable calibrator
     * @param pval 
     */
    public void calibrate(ParameterValue pval) {
        doCalibrate(pval, pval.getParameter().getParameterType());
    }

    private void doCalibrate(ParameterValue pval, ParameterType ptype) {
        if (ptype instanceof EnumeratedParameterType) {
            calibrateEnumerated((EnumeratedParameterType) ptype, pval);
        } else if (ptype instanceof IntegerParameterType) {
            calibrateInteger((IntegerParameterType) ptype, pval);
        } else if (ptype instanceof FloatParameterType) {
            calibrateFloat((FloatParameterType) ptype, pval);
        } else if (ptype instanceof BinaryParameterType) {
            calibrateBinary((BinaryParameterType) ptype, pval);
        } else if (ptype instanceof StringParameterType) {
            calibrateString((StringParameterType) ptype, pval);
        } else if (ptype instanceof BooleanParameterType) {
            calibrateBoolean((BooleanParameterType) ptype, pval);
        } else {
            throw new IllegalArgumentException("Extraction of "+ptype+" not implemented");
        }
    }

    private static void calibrateEnumerated(EnumeratedParameterType ept, ParameterValue pval) {
        Value rawValue = pval.getRawValue();
        if (rawValue.getType() == Type.UINT32) {
            pval.setStringValue(ept.calibrate(rawValue.getUint32Value()));
        } else if (rawValue.getType() == Type.UINT64) {
            pval.setStringValue(ept.calibrate(rawValue.getUint64Value()));
        } else if (rawValue.getType() == Type.SINT32) {
            pval.setStringValue(ept.calibrate(rawValue.getSint32Value()));
        } else if (rawValue.getType() == Type.SINT64) {
            pval.setStringValue(ept.calibrate(rawValue.getSint64Value()));
        }  else if (rawValue.getType() == Type.FLOAT) { // added for obcp simulator
            pval.setStringValue(ept.calibrate((long)rawValue.getFloatValue()));
        } else if (rawValue.getType() == Type.STRING) {
            try {
                long l = Long.decode(rawValue.getStringValue());
                pval.setStringValue(ept.calibrate(l));
            } catch (NumberFormatException e) {
                log.warn("{}: failed to parse string '{}' to long", ept.getName(), rawValue.getStringValue());
                pval.setAcquisitionStatus(AcquisitionStatus.INVALID);
            }
        } else {
            throw new IllegalStateException("Unsupported raw value type '"+rawValue.getType()+"' cannot be calibrated as an enumeration");
        }
    }

    private static void calibrateBoolean(BooleanParameterType bpt, ParameterValue pval) {
        Value rawValue = pval.getRawValue();
        if (rawValue.getType() == Type.UINT32) {
            pval.setBooleanValue(rawValue.getUint32Value() != 0);
        } else if (rawValue.getType() == Type.UINT64) {
            pval.setBooleanValue(rawValue.getUint64Value() != 0);
        } else if (rawValue.getType() == Type.SINT32) {
            pval.setBooleanValue(rawValue.getSint32Value() != 0);
        } else if (rawValue.getType() == Type.SINT64) {
            pval.setBooleanValue(rawValue.getSint64Value() != 0);
        } else if (rawValue.getType() == Type.FLOAT) {
            pval.setBooleanValue(rawValue.getFloatValue() != 0);
        } else if (rawValue.getType() == Type.DOUBLE) {
            pval.setBooleanValue(rawValue.getDoubleValue() != 0);
        } else if (rawValue.getType() == Type.STRING) {
            pval.setBooleanValue(rawValue.getStringValue() != null && !rawValue.getStringValue().isEmpty());
        } else if (rawValue.getType() == Type.BOOLEAN) {
            pval.setBooleanValue(rawValue.getBooleanValue());
        } else if (rawValue.getType() == Type.BINARY) {
            ByteBuffer buf = ByteBuffer.wrap(rawValue.getBinaryValue());
            pval.setBooleanValue(false);
            while(buf.hasRemaining()) {
                if(buf.get()!=0xFF) {
                    pval.setBooleanValue(true);
                    break;
                }
            }
        } else {
            throw new IllegalStateException("Unsupported raw value type '"+rawValue.getType()+"' cannot be calibrated as a boolean");
        }
    }

    private static void calibrateBinary(BinaryParameterType bpt, ParameterValue pval) {
        pval.setEngineeringValue(pval.getRawValue());
    }

    private void calibrateInteger(IntegerParameterType ipt, ParameterValue pval) {
        Value rawValue = pval.getRawValue();
        if (rawValue.getType() == Type.UINT32) {
            doIntegerCalibration(ipt, pval, rawValue.getUint32Value()&0xFFFFFFFFL);
        } else if (rawValue.getType() == Type.UINT64) {
            doIntegerCalibration(ipt, pval, rawValue.getUint64Value());
        } else if (rawValue.getType() == Type.SINT32) {
            doIntegerCalibration(ipt, pval, rawValue.getSint32Value());
        } else if (rawValue.getType() == Type.SINT64) {
            doIntegerCalibration(ipt, pval, rawValue.getSint64Value());
        } else if (rawValue.getType() == Type.FLOAT) {
            doIntegerCalibration(ipt, pval, (long)rawValue.getFloatValue());
        } else if (rawValue.getType() == Type.STRING) {
            try {
                long l = Long.decode(rawValue.getStringValue());
                doIntegerCalibration(ipt, pval, l);
            } catch (NumberFormatException e) {
                log.warn("{}: failed to parse string '{}' to long", ipt.getName(), rawValue.getStringValue());
                pval.setAcquisitionStatus(AcquisitionStatus.INVALID);
            }
        } else {
            throw new IllegalStateException("Unsupported raw value type '"+rawValue.getType()+"' cannot be converted to integer");
        }
    }

    private void doIntegerCalibration(IntegerParameterType ipt, ParameterValue pval, long longValue) {
        CalibratorProc calibrator = pcontext.getCalibrator(ipt.getEncoding());

        long longCalValue = (calibrator == null) ? longValue: (long)calibrator.calibrate(longValue);

        if (ipt.getSizeInBits() <= 32) {
            if (ipt.isSigned())
                pval.setSignedIntegerValue((int) longCalValue);
            else
                pval.setUnsignedIntegerValue((int) longCalValue);
        } else {
            if (ipt.isSigned())
                pval.setSignedLongValue(longCalValue);
            else
                pval.setUnsignedLongValue(longCalValue);
        }
    }

    private static void calibrateString(StringParameterType spt, ParameterValue pval) {
        Value rawValue = pval.getRawValue();
        if(rawValue.getType() == Type.STRING) {
            pval.setEngineeringValue(rawValue);
        } else {
            throw new IllegalStateException("Unsupported raw value type '"+rawValue.getType()+"' cannot be converted to string");
        }
    }

    private void calibrateFloat(FloatParameterType fpt, ParameterValue pval) {
        Value rawValue = pval.getRawValue();
        if(rawValue.getType() == Type.FLOAT) {
            doFloatCalibration(fpt, pval, rawValue.getFloatValue());
        } else if(rawValue.getType() == Type.DOUBLE) {
            doFloatCalibration(fpt, pval, rawValue.getDoubleValue());
        } else if(rawValue.getType() == Type.STRING) {
            try {
                Double d = Double.parseDouble(rawValue.getStringValue());
                doFloatCalibration(fpt, pval, d);
            } catch (NumberFormatException e) {
                log.warn("{}: failed to parse string '{}' to double", fpt.getName(), rawValue.getStringValue());
                pval.setAcquisitionStatus(AcquisitionStatus.INVALID);
            }
        } else if(rawValue.getType() == Type.UINT32) {
            doFloatCalibration(fpt, pval, rawValue.getUint32Value());
        } else if(rawValue.getType() == Type.UINT64) {
            doFloatCalibration(fpt, pval, rawValue.getUint64Value());
        } else if(rawValue.getType() == Type.SINT32) {
            doFloatCalibration(fpt, pval, rawValue.getSint32Value());
        } else if(rawValue.getType() == Type.SINT64) {
            doFloatCalibration(fpt, pval, rawValue.getSint64Value());
        } else {
            throw new IllegalStateException("Unsupported raw value type '"+rawValue.getType()+"' cannot be converted to float");
        }
    }

    private void doFloatCalibration(FloatParameterType fpt, ParameterValue pval, double doubleValue) {
        CalibratorProc calibrator = pcontext.getCalibrator(fpt.getEncoding());

        double doubleCalValue = (calibrator == null) ? doubleValue:calibrator.calibrate(doubleValue);
        if(fpt.getSizeInBits() == 32) {
            pval.setFloatValue((float) doubleCalValue);
        } else {
            pval.setDoubleValue(doubleCalValue);
        }
    }

    /**
     * Checks that a value can be assigned to a parameter as enginnering value
     * Throws an IllegalArgumentException if not
     *
     * @param p
     * @param engValue
     */
    public static void checkEngValueAssignment(Parameter p, Value engValue) {
        ParameterType ptype = p.getParameterType();
        if(!allowedAssignments.containsEntry(ptype.getClass(), engValue.getType())) {
            throw new IllegalArgumentException("Cannot assign "+ptype.getTypeAsString()+" from "+engValue.getType());
        }
    }
    
    public static Value parseString(ParameterType type, String paramValue) {
        Value v;
        if(type instanceof IntegerParameterType) {
            IntegerParameterType intType = (IntegerParameterType) type;
            if(intType.isSigned()) {
                long l = Long.decode(paramValue);
                IntegerValidRange vr = ((IntegerArgumentType)type).getValidRange();
                if(vr!=null) {
                    if(!ValidRangeChecker.checkIntegerRange(vr, l)) {
                        throw new IllegalArgumentException("Value "+l+" is not in the range required for the type "+type);
                    }
                }
                v = ValueUtility.getSint64Value(l);
            } else {
                long l = UnsignedLongs.decode(paramValue);
                IntegerValidRange vr = ((IntegerParameterType)type).getValidRange();
                if(vr!=null) {
                    if(!ValidRangeChecker.checkUnsignedIntegerRange(vr, l)) {
                        throw new IllegalArgumentException("Value "+l+" is not in the range required for the type "+type);
                    }
                }
                v = ValueUtility.getUint64Value(l);
            }
            
       } else if(type instanceof FloatParameterType) {
            double d = Double.parseDouble(paramValue);
            FloatValidRange vr = ((FloatParameterType)type).getValidRange();
            if(vr!=null) {
                if(!ValidRangeChecker.checkFloatRange(vr, d)) {
                    throw new IllegalArgumentException("Value "+d+" is not in the range required for the type "+type);
                }
            }
            v = ValueUtility.getDoubleValue(d);
        } else if(type instanceof StringParameterType) {
            v = ValueUtility.getStringValue(paramValue);
            IntegerRange r = ((StringParameterType)type).getSizeRangeInCharacters();

            if(r!=null) {
                int length = paramValue.length();
                if (length<r.getMinInclusive()) {
                    throw new IllegalArgumentException("Value "+paramValue+" supplied for parameter fo type "+type+" does not satisfy minimum length of "+r.getMinInclusive());
                }
                if(length>r.getMaxInclusive()) {
                    throw new IllegalArgumentException("Value "+paramValue+" supplied for parameter fo type "+type+" does not satisfy maximum length of "+r.getMaxInclusive());
                }
            }

        } else if (type instanceof BinaryParameterType) {
            byte[] b = StringConverter.hexStringToArray(paramValue);
            v = ValueUtility.getBinaryValue(b);
        } else if (type instanceof EnumeratedArgumentType) {
            EnumeratedArgumentType enumType = (EnumeratedArgumentType)type;
            List<ValueEnumeration> vlist = enumType.getValueEnumerationList();
            boolean found =false;
            for(ValueEnumeration ve:vlist) {
                if(ve.getLabel().equals(paramValue)) {
                    found = true;
                }
            }
            if(!found) {
                throw new IllegalArgumentException("Value '"+paramValue+"' supplied for enumeration argument cannot be found in enumeration list "+vlist);
            }
            v = ValueUtility.getStringValue(paramValue);
        } else if (type instanceof BooleanParameterType) {
            boolean b = Boolean.parseBoolean(paramValue);
            v = ValueUtility.getBooleanValue(b);
        } else {
            throw new IllegalArgumentException("Cannot parse values of type "+type);
        }
        return v;
    }

    public static Value getDefaultValue(ParameterType type) {
        Value v;
        if(type instanceof IntegerParameterType) {
            IntegerParameterType intType = (IntegerParameterType) type;
            String sv = intType.getInitialValue();
            if(intType.isSigned()) {
                long l = sv==null?0:Long.decode(sv);
                v = ValueUtility.getSint64Value(l);
            } else {
                long l = sv==null?0:UnsignedLongs.decode(sv);
                v = ValueUtility.getUint64Value(l);
            }
            
       } else if(type instanceof FloatParameterType) {
            Double d = ((FloatParameterType)type).getInitialValue();
            if(d==null) {
                d = 0.0;
            }
            v = ValueUtility.getDoubleValue(d);
        } else if(type instanceof StringParameterType) {
            String sv = ((StringParameterType)type).getInitialValue();
            if(sv==null) {
                sv="";
            }
            v = ValueUtility.getStringValue(sv);
        } else if (type instanceof BinaryParameterType) {
            byte[] b = ((BinaryParameterType)type).getInitialValue();
            if(b==null) {
                b = new byte[0];
            }
            v = ValueUtility.getBinaryValue(b);
        } else if (type instanceof EnumeratedArgumentType) {
            EnumeratedArgumentType enumType = (EnumeratedArgumentType)type;
            String sv = enumType.getInitialValue();
            if(sv==null) {
                sv = enumType.getValueEnumerationList().get(0).getLabel();
            }
            v = ValueUtility.getStringValue(sv);
        } else if (type instanceof BooleanParameterType) {
            Boolean b = ((BooleanParameterType)type).getInitialValue();
            if(b==null) {
                b = Boolean.FALSE;
            }
            v = ValueUtility.getBooleanValue(b);
        } else {
            throw new IllegalArgumentException("Cannot parse values of type "+type);
        }
        return v;
    }
}
