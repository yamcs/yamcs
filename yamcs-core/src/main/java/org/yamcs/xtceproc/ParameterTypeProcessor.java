package org.yamcs.xtceproc;

import java.nio.ByteBuffer;

import org.yamcs.ParameterValue;
import org.yamcs.protobuf.Yamcs.Value;
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
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.StringParameterType;


public class ParameterTypeProcessor {
    ProcessingContext pcontext;

    ParameterTypeProcessor(ProcessingContext pcontext) {
        this.pcontext=pcontext;
    }

    /**
     *  Extracts the parameter from the packet.
     * @return value of the parameter after extraction
     */
    public ParameterValue extract(ParameterType ptype) {
        ParameterValue pv=new ParameterValue(null);
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
            ByteBuffer buf=rawValue.getBinaryValue().asReadOnlyByteBuffer();
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
        pval.setBinaryValue(pval.getRawValue().getBinaryValue().toByteArray());
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
        } else if (rawValue.getType() == Type.STRING) {
            doIntegerCalibration(ipt, pval, Long.valueOf(rawValue.getStringValue()));
        } else {
            throw new IllegalStateException("Unsupported raw value type '"+rawValue.getType()+"' cannot be converted to integer");
        }
    }
    
    private static void doIntegerCalibration(IntegerParameterType ipt, ParameterValue pval, long longValue) {
        Calibrator calibrator=null;
        DataEncoding de=ipt.getEncoding();
        if(de instanceof IntegerDataEncoding) {
            calibrator=((IntegerDataEncoding) de).getDefaultCalibrator();
        } else {
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
            doFloatCalibration(fpt, pval, Double.valueOf(rawValue.getStringValue()));
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
}
