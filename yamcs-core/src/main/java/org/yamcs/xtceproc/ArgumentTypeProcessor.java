package org.yamcs.xtceproc;

import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.BinaryArgumentType;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.FloatArgumentType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.StringArgumentType;

public class ArgumentTypeProcessor {
	
	public static Value decalibrate(ArgumentType atype, Value v) {
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
		} else {
			throw new IllegalArgumentException("decalibration for "+atype+" not implemented");
		}
	}

	private static Value decalibrateEnumerated(EnumeratedArgumentType atype, Value v) {
		if(v.getType()!=Value.Type.STRING) throw new IllegalArgumentException("Enumerated decalibrations only available for strings");
		return Value.newBuilder().setType(Value.Type.SINT64).setSint64Value(atype.decalibrate(v.getStringValue())).build();
	}
	
	
    private static Value decalibrateInteger(IntegerArgumentType ipt, Value v) {
        if (v.getType() == Type.UINT32) {
           return doIntegerDecalibration(ipt, v.getUint32Value()&0xFFFFFFFFL);
        } else if (v.getType() == Type.UINT64) {
            return doIntegerDecalibration(ipt, v.getUint64Value());
        } else if (v.getType() == Type.SINT32) {
            return doIntegerDecalibration(ipt, v.getSint32Value());
        } else if (v.getType() == Type.SINT64) {
            return doIntegerDecalibration(ipt, v.getSint64Value());
        } else if (v.getType() == Type.STRING) {
            return doIntegerDecalibration(ipt, Long.valueOf(v.getStringValue()));
        } else {
            throw new IllegalStateException("Unsupported raw value type '"+v.getType()+"' cannot be converted to integer");
        }
    }
    
    private static Value doIntegerDecalibration(IntegerArgumentType ipt, long v) {
        Calibrator calibrator=null;
        DataEncoding de=ipt.getEncoding();
        if(de instanceof IntegerDataEncoding) {
            calibrator=((IntegerDataEncoding) de).getDefaultCalibrator();
        } else {
            throw new IllegalStateException("Unsupported integer encoding of type: "+de);
        }
        
        Value raw;
        long longDecalValue = (calibrator == null) ? v:calibrator.calibrate(v).longValue(); 
        
        if (ipt.getSizeInBits() <= 32) {
            if (ipt.isSigned()) {
                raw = Value.newBuilder().setType(Value.Type.SINT32).setSint32Value((int) longDecalValue).build();
            } else {
                raw = Value.newBuilder().setType(Value.Type.UINT32).setUint32Value((int) longDecalValue).build();
            }
        } else {
            if (ipt.isSigned()) {
            	raw = Value.newBuilder().setType(Value.Type.UINT32).setSint64Value(longDecalValue).build();            	
            } else {
            	raw = Value.newBuilder().setType(Value.Type.UINT32).setUint64Value(longDecalValue).build();
            }
        }
        return raw;
    }

	
	private static Value decalibrateFloat(FloatArgumentType fat, Value v) {
        if(v.getType() == Type.FLOAT) {
            return doFloatCalibration(fat, v.getFloatValue());
        } else if(v.getType() == Type.DOUBLE) {
        	return doFloatCalibration(fat, v.getDoubleValue());
        } else if(v.getType() == Type.STRING) {
        	return doFloatCalibration(fat, Double.valueOf(v.getStringValue()));
        } else if(v.getType() == Type.UINT32) {
        	return doFloatCalibration(fat, v.getUint32Value());
        } else if(v.getType() == Type.UINT64) {
        	return doFloatCalibration(fat, v.getUint64Value());
        } else if(v.getType() == Type.SINT32) {
        	return doFloatCalibration(fat, v.getSint32Value());
        } else if(v.getType() == Type.SINT64) {
        	return  doFloatCalibration(fat, v.getSint64Value());
        } else {
            throw new IllegalArgumentException("Unsupported value type '"+v.getType()+"' cannot be converted to float");
        }
    }
    
    private static Value doFloatCalibration(FloatArgumentType fat, double doubleValue) {
        Calibrator calibrator=null;
        DataEncoding de=fat.getEncoding();
        if(de instanceof FloatDataEncoding) {
            calibrator=((FloatDataEncoding) de).getDefaultCalibrator();
        } else if(de instanceof IntegerDataEncoding) {
            calibrator=((IntegerDataEncoding) de).getDefaultCalibrator();
        } else {
            throw new IllegalStateException("Unsupported float encoding of type: "+de);
        }
        
        double doubleCalValue = (calibrator == null) ? doubleValue:calibrator.calibrate(doubleValue);
        Value raw;
        if(fat.getSizeInBits() == 32) {
            raw = Value.newBuilder().setType(Value.Type.FLOAT).setFloatValue((float)doubleCalValue).build();
        } else {
        	raw = Value.newBuilder().setType(Value.Type.DOUBLE).setDoubleValue(doubleCalValue).build();
        }
        return raw;
    }    

    private static Value decalibrateString(StringArgumentType sat, Value v) {
    	Value raw;
    	if(v.getType() == Type.STRING) {
    		raw = v;
        } else {
            throw new IllegalStateException("Unsupported value type '"+v.getType()+"' cannot be converted to string");
        }
    	return raw;
    }
    
    
    private static Value decalibrateBinary(BinaryArgumentType bat, Value v) {
    	Value raw;
    	if(v.getType() == Type.BINARY) {
    		raw = v;
        } else {
            throw new IllegalStateException("Unsupported value type '"+v.getType()+"' cannot be converted to binary");
        }
    	return raw;
    }
}
