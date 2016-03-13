package org.yamcs.xtceproc;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.StringParameterType;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;

public class ParameterTypeProcessor {
    ProcessingContext pcontext;
    static Logger log=LoggerFactory.getLogger(ParameterTypeProcessor.class.getName());
    
    ParameterTypeProcessor(ProcessingContext pcontext) {
        this.pcontext=pcontext;
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
     *  Extracts the parameter from the packet.
     * @return value of the parameter after extraction
     */
    public ParameterValue extract(Parameter param) {
        ParameterType ptype = param.getParameterType();
        ParameterValue pv=new ParameterValue(param);
        pv.setAbsoluteBitOffset(pcontext.containerAbsoluteByteOffset*8+pcontext.bitPosition);
        pv.setBitSize(((BaseDataType)ptype).getEncoding().getSizeInBits());
        pcontext.dataEncodingProcessor.extractRaw(((BaseDataType)ptype).getEncoding(), pv);
        doCalibrate(pv, ptype);
        return pv;
    }
   
    /**
     * Sets the value of a pval, based on the raw value and the applicable calibrator
     */
    public static void calibrate(ParameterValue pval) {
        doCalibrate(pval, pval.getParameter().getParameterType());
    }
    
    private static void doCalibrate(ParameterValue pval, ParameterType ptype) {
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
        pval.setBinaryValue(pval.getRawValue().getBinaryValue());
    }

    private static void calibrateInteger(IntegerParameterType ipt, ParameterValue pval) {
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
    
    private static void doIntegerCalibration(IntegerParameterType ipt, ParameterValue pval, long longValue) {
        Calibrator calibrator=null;
        DataEncoding de=ipt.getEncoding();
        if(de instanceof IntegerDataEncoding) {
            calibrator=((IntegerDataEncoding) de).getDefaultCalibrator();
        }
        else if(de instanceof  FloatDataEncoding)
        {
            calibrator=((FloatDataEncoding) de).getDefaultCalibrator();
        }
        else {
            throw new IllegalStateException("Unsupported float encoding of type: "+de);
        }
        
        long longCalValue = (calibrator == null) ? longValue:calibrator.calibrate(longValue).longValue(); 
        
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
            pval.setStringValue(rawValue.getStringValue());
        } else {
            throw new IllegalStateException("Unsupported raw value type '"+rawValue.getType()+"' cannot be converted to string");
        }
    }

    private static void calibrateFloat(FloatParameterType fpt, ParameterValue pval) {
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
    
    private static void doFloatCalibration(FloatParameterType fpt, ParameterValue pval, double doubleValue) {
        Calibrator calibrator=null;
        DataEncoding de=fpt.getEncoding();
        if(de instanceof FloatDataEncoding) {
            calibrator=((FloatDataEncoding) de).getDefaultCalibrator();
        } else if(de instanceof IntegerDataEncoding) {
            calibrator=((IntegerDataEncoding) de).getDefaultCalibrator();
        } else {
            throw new IllegalStateException("Unsupported float encoding of type: "+de);
        }
        
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
}
