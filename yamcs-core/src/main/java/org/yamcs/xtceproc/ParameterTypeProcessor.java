package org.yamcs.xtceproc;

import java.nio.ByteBuffer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.AbsoluteTimeParameterType;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.CriteriaEvaluator;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.FloatValidRange;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.IntegerRange;
import org.yamcs.xtce.IntegerValidRange;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.ReferenceTime;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtce.TimeEpoch;
import org.yamcs.xtce.TimeEpoch.CommonEpochs;
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
    ProcessorData pdata;
    static Logger log=LoggerFactory.getLogger(ParameterTypeProcessor.class.getName());

    public ParameterTypeProcessor(ProcessorData pdata) {
        this.pdata = pdata;
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
    public void calibrate(ContainerProcessingContext pcontext, ParameterValue pval) {
        doCalibrate(pcontext.result.params, pcontext.criteriaEvaluator, pval, pval.getParameter().getParameterType());
    }
    public void calibrate(ParameterValue pval) {
        doCalibrate(null, null, pval, pval.getParameter().getParameterType());
    }

    private void doCalibrate(ParameterValueList pvalues, CriteriaEvaluator contextEvaluator, ParameterValue pval, ParameterType ptype) {
        if (ptype instanceof EnumeratedParameterType) {
            calibrateEnumerated((EnumeratedParameterType) ptype, pval);
        } else if (ptype instanceof IntegerParameterType) {
            calibrateInteger(contextEvaluator, (IntegerParameterType) ptype, pval);
        } else if (ptype instanceof FloatParameterType) {
            calibrateFloat(contextEvaluator, (FloatParameterType) ptype, pval);
        } else if (ptype instanceof BinaryParameterType) {
            calibrateBinary((BinaryParameterType) ptype, pval);
        } else if (ptype instanceof StringParameterType) {
            calibrateString((StringParameterType) ptype, pval);
        } else if (ptype instanceof BooleanParameterType) {
            calibrateBoolean((BooleanParameterType) ptype, pval);
        } else if (ptype instanceof AbsoluteTimeParameterType) {
            calibrateAbsoluteTime(pvalues, (AbsoluteTimeParameterType) ptype, pval);
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
        if(ValueUtility.processAsLong(rawValue, l -> {
            pval.setBooleanValue(l != 0);            
        })) return;
        
        if(ValueUtility.processAsDouble(rawValue, d -> {
            pval.setBooleanValue(d != 0);            
        })) return;
        
        if (rawValue.getType() == Type.STRING) {
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

    private void calibrateInteger(CriteriaEvaluator contextEvaluator, IntegerParameterType ipt, ParameterValue pval) {
        Value rawValue = pval.getRawValue();
        if(ValueUtility.processAsLong(rawValue, l -> {
            doIntegerCalibration(contextEvaluator, ipt, pval, l);           
        })) return;
        
        if(ValueUtility.processAsDouble(rawValue, d -> {
            doIntegerCalibration(contextEvaluator, ipt, pval, (long)d);            
        })) return;
       
        if (rawValue.getType() == Type.STRING) {
            try {
                long l = Long.decode(rawValue.getStringValue());
                doIntegerCalibration(contextEvaluator, ipt, pval, l);
            } catch (NumberFormatException e) {
                log.warn("{}: failed to parse string '{}' to long", ipt.getName(), rawValue.getStringValue());
                pval.setAcquisitionStatus(AcquisitionStatus.INVALID);
            }
        } else {
            throw new IllegalStateException("Unsupported raw value type '"+rawValue.getType()+"' cannot be converted to integer");
        }
    }

    private void doIntegerCalibration(CriteriaEvaluator contextEvaluator, IntegerParameterType ipt, ParameterValue pval, long longValue) {
        CalibratorProc calibrator = pdata.getCalibrator(contextEvaluator, ipt.getEncoding());

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

    private void calibrateFloat(CriteriaEvaluator contextEvaluator, FloatParameterType ptype, ParameterValue pval) {
        Value rawValue = pval.getRawValue();
        
        if(ValueUtility.processAsDouble(rawValue, d -> {
            doFloatCalibration(contextEvaluator, ptype, d, pval);
        })) return;
            
        if(ValueUtility.processAsLong(rawValue, l -> {
            doFloatCalibration(contextEvaluator, ptype, l, pval);
        })) return;
            
        if(rawValue.getType() == Type.STRING) {
            try {
                Double d = Double.parseDouble(rawValue.getStringValue());
                doFloatCalibration(contextEvaluator, ptype, d, pval);
            } catch (NumberFormatException e) {
                log.warn("{}: failed to parse string '{}' to double", ptype.getName(), rawValue.getStringValue());
                pval.setAcquisitionStatus(AcquisitionStatus.INVALID);
            }
        } else {
            throw new IllegalStateException("Unsupported raw value type '"+rawValue.getType()+"' cannot be converted to float");
        }
    }

    private void doFloatCalibration(CriteriaEvaluator contextEvaluator, FloatParameterType ptype, double doubleValue, ParameterValue pval) {
        CalibratorProc calibrator = pdata.getCalibrator(contextEvaluator, ptype.getEncoding());

        double doubleCalValue = (calibrator == null) ? doubleValue:calibrator.calibrate(doubleValue);
        if(ptype.getSizeInBits() == 32) {
            pval.setFloatValue((float) doubleCalValue);
        } else {
            pval.setDoubleValue(doubleCalValue);
        }
    }


    private void calibrateAbsoluteTime(ParameterValueList context, AbsoluteTimeParameterType ptype, ParameterValue pval) {
        ReferenceTime rtime = ptype.getReferenceTime();
        TimeEpoch epoch = rtime.getEpoch();
        Value rawValue = pval.getRawValue();
        long referenceTime = 0 ;
        
        if(epoch!=null) {
            referenceTime = getEpochTime(epoch);
        } else {
            ParameterInstanceRef ref = rtime.getOffsetFrom();
            if(ref!=null) {
                referenceTime = getParaReferenceTime(context, pval.getParameter(), ref);
                if(referenceTime==TimeEncoding.INVALID_INSTANT) {
                    pval.setAcquisitionStatus(AcquisitionStatus.INVALID);
                }
            } else {
                log.warn("{}: cannot calibrate with a epoch without a reference", ptype.getName());
                pval.setAcquisitionStatus(AcquisitionStatus.INVALID);
            }
        }
        long rt = referenceTime;
        
        if(ValueUtility.processAsLong(rawValue, l -> {
            pval.setEngineeringValue(ValueUtility.getTimestampValue(computeTime(ptype, rt, l)));
        })) return;
        
        if(ValueUtility.processAsDouble(rawValue, d -> {
            pval.setEngineeringValue(ValueUtility.getTimestampValue(computeTime(ptype, rt, d)));
        })) return;
        
        throw new IllegalStateException("Unsupported raw value type '"+rawValue.getType()+"' cannot be converted to absolute time");
    }
    
    private long computeTime(AbsoluteTimeParameterType ptype, long epochMillisec, long offset) {
        if(ptype.needsScaling()) {
            return (long)(epochMillisec + 1000*ptype.getOffset() + 1000*ptype.getScale()*offset);
        } else {
            return epochMillisec + 1000*offset;
        }
    }
    private long computeTime(AbsoluteTimeParameterType ptype, long epochMillisec, double offset) {
        if(ptype.needsScaling()) {
            return (long)(epochMillisec + 1000*ptype.getOffset()+1000*ptype.getScale()*offset);
        } else {
            return (long)(epochMillisec + offset);
        }
    }
    private long getParaReferenceTime(ParameterValueList context, Parameter p, ParameterInstanceRef ref) {
        if(context==null) {
            log.warn("{}: no parameter processing context avaialble", p.getQualifiedName());
            return TimeEncoding.INVALID_INSTANT;
        }
        ParameterValue pv = context.getLastInserted(ref.getParameter());
        if(pv==null) {
            log.warn("{}: no instance of {} found in the processing context", p.getQualifiedName(), ref.getParameter().getQualifiedName());
            return TimeEncoding.INVALID_INSTANT;
        }
        Value v = pv.getEngValue();
        
        if(v.getType() != Type.TIMESTAMP) {
            log.warn("{}: instance {} is of type {} instead of required TIMESTAMP", p.getQualifiedName(), ref.getParameter().getQualifiedName());
            return TimeEncoding.INVALID_INSTANT;
        }
        return v.getTimestampValue();
    }

    private long getEpochTime(TimeEpoch epoch) {
        CommonEpochs ce = epoch.getCommonEpoch();
        
        if(ce!=null) {
            switch(ce) {
            case GPS:
                return TimeEncoding.fromGpsMillisec(0);
            case J2000:
                return TimeEncoding.fromJ2000Millisec(0);
            case TAI:
                 return TimeEncoding.fromTaiMillisec(0);
            case UNIX:
                return TimeEncoding.fromUnixMillisec(0);
            default:
                throw new IllegalStateException("Unknonw epoch "+ce);
            }
        } else {
            return TimeEncoding.parse(epoch.getDateTime());
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
