package org.yamcs.xtceproc;

import java.util.List;

import org.yamcs.ErrorInCommand;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.BinaryArgumentType;
import org.yamcs.xtce.BooleanArgumentType;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.FloatArgumentType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatValidRange;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerRange;
import org.yamcs.xtce.IntegerValidRange;
import org.yamcs.xtce.StringArgumentType;
import org.yamcs.xtce.ValueEnumeration;

import com.google.common.primitives.UnsignedLongs;

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
        } else if (atype instanceof BooleanArgumentType) {
            return decalibrateBoolean((BooleanArgumentType) atype, v);
        } else {
            throw new IllegalArgumentException("decalibration for "+atype+" not implemented");
        }
    }

    private static Value decalibrateEnumerated(EnumeratedArgumentType atype, Value v) {
        if(v.getType()!=Type.STRING) throw new IllegalArgumentException("Enumerated decalibrations only available for strings");
        
        return ValueUtility.getSint64Value(atype.decalibrate(v.getStringValue()));
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
        }
        else if(de instanceof FloatDataEncoding) {
            return doFloatDecalibration(ipt.getEncoding(), ipt.getSizeInBits(), v);
        }
        else {
            throw new IllegalStateException("Unsupported integer encoding of type: "+de);
        }

        Value raw;
        long longDecalValue = (calibrator == null) ? v:calibrator.calibrate(v).longValue(); 

        if (ipt.getSizeInBits() <= 32) {
            if (ipt.isSigned()) {
                raw = ValueUtility.getSint32Value((int) longDecalValue);
            } else {
                raw = ValueUtility.getUint32Value((int) longDecalValue);
            }
        } else {
            if (ipt.isSigned()) {
                raw = ValueUtility.getSint64Value(longDecalValue);            	
            } else {
                raw = ValueUtility.getUint64Value(longDecalValue);
            }
        }
        return raw;
    }

    private static Value decalibrateBoolean(BooleanArgumentType ipt, Value v) {
        if (v.getType() != Type.BOOLEAN) {
            throw new IllegalStateException("Unsupported raw value type '"+v.getType()+"' cannot be converted to boolean");
        } 
        return v;
    }
    private static Value decalibrateFloat(FloatArgumentType fat, Value v) {
        if(v.getType() == Type.FLOAT) {
            return doFloatDecalibration(fat.getEncoding(), fat.getSizeInBits(), v.getFloatValue());
        } else if(v.getType() == Type.DOUBLE) {
            return doFloatDecalibration(fat.getEncoding(), fat.getSizeInBits(), v.getDoubleValue());
        } else if(v.getType() == Type.STRING) {
            return doFloatDecalibration(fat.getEncoding(), fat.getSizeInBits(), Double.valueOf(v.getStringValue()));
        } else if(v.getType() == Type.UINT32) {
            return doFloatDecalibration(fat.getEncoding(), fat.getSizeInBits(), v.getUint32Value());
        } else if(v.getType() == Type.UINT64) {
            return doFloatDecalibration(fat.getEncoding(), fat.getSizeInBits(), v.getUint64Value());
        } else if(v.getType() == Type.SINT32) {
            return doFloatDecalibration(fat.getEncoding(), fat.getSizeInBits(), v.getSint32Value());
        } else if(v.getType() == Type.SINT64) {
            return  doFloatDecalibration(fat.getEncoding(), fat.getSizeInBits(), v.getSint64Value());
        } else {
            throw new IllegalArgumentException("Unsupported value type '"+v.getType()+"' cannot be converted to float");
        }
    }

    private static Value doFloatDecalibration(DataEncoding de, int sizeInBits, double doubleValue) {
        Calibrator calibrator=null;
        if(de instanceof FloatDataEncoding) {
            calibrator=((FloatDataEncoding) de).getDefaultCalibrator();
        } else if(de instanceof IntegerDataEncoding) {
            calibrator=((IntegerDataEncoding) de).getDefaultCalibrator();
        } else {
            throw new IllegalStateException("Unsupported float encoding of type: "+de);
        }

        double doubleCalValue = (calibrator == null) ? doubleValue:calibrator.calibrate(doubleValue);
        Value raw;
        if(sizeInBits == 32) {
            raw = ValueUtility.getFloatValue((float)doubleCalValue);
        } else {
            raw = ValueUtility.getDoubleValue(doubleCalValue);
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

    public static Value getInitialValue(ArgumentType type) {
        if (type instanceof IntegerArgumentType) {
            if(((IntegerArgumentType) type).getInitialValue() == null)
                return null;
            return ValueUtility.getStringValue(((IntegerArgumentType) type).getInitialValue());
        } else if (type instanceof FloatArgumentType) {
            if(((FloatArgumentType) type).getInitialValue() == null)
                return null;
            return ValueUtility.getDoubleValue(((FloatArgumentType) type).getInitialValue());
        } else if (type instanceof StringArgumentType) {
            if(((StringArgumentType) type).getInitialValue() == null)
                return null;
            return ValueUtility.getStringValue(((StringArgumentType) type).getInitialValue());
        } else if (type instanceof BinaryArgumentType) {

            if(((BinaryArgumentType) type).getInitialValue() == null)
                return null;
            return ValueUtility.getBinaryValue(((BinaryArgumentType) type).getInitialValue());
        } else if (type instanceof EnumeratedArgumentType) {

            if(((EnumeratedArgumentType) type).getInitialValue() == null)
                return null;
            return ValueUtility.getStringValue(((EnumeratedArgumentType) type).getInitialValue());
        }
        return null;
    }

    public static Value parseAndCheckRange(ArgumentType type, String argumentValue) throws ErrorInCommand {
        Value v;
        if(type instanceof IntegerArgumentType) {
            IntegerArgumentType intType = (IntegerArgumentType) type;
            if(intType.isSigned()) {
                long l = Long.decode(argumentValue);
                IntegerValidRange vr = ((IntegerArgumentType)type).getValidRange();
                if(vr!=null) {
                    if(!ValidRangeChecker.checkIntegerRange(vr, l)) {
                        throw new ErrorInCommand("Value "+l+" is not in the range required for the type "+type);
                    }
                }
                v = ValueUtility.getSint64Value(l);
            } else {
                long l = UnsignedLongs.decode(argumentValue);
                IntegerValidRange vr = ((IntegerArgumentType)type).getValidRange();
                if(vr!=null) {
                    if(!ValidRangeChecker.checkUnsignedIntegerRange(vr, l)) {
                        throw new ErrorInCommand("Value "+l+" is not in the range required for the type "+type);
                    }
                }
                v = ValueUtility.getUint64Value(l);
            }
            
       } else if(type instanceof FloatArgumentType) {
            double d = Double.parseDouble(argumentValue);
            FloatValidRange vr = ((FloatArgumentType)type).getValidRange();
            if(vr!=null) {
                if(!ValidRangeChecker.checkFloatRange(vr, d)) {
                    throw new ErrorInCommand("Value "+d+" is not in the range required for the type "+type);
                }
            }
            v = ValueUtility.getDoubleValue(d);
        } else if(type instanceof StringArgumentType) {
            v = ValueUtility.getStringValue(argumentValue);
            IntegerRange r = ((StringArgumentType)type).getSizeRangeInCharacters();

            if(r!=null) {
                int length = argumentValue.length();
                if (length<r.getMinInclusive()) {
                    throw new ErrorInCommand("Value "+argumentValue+" supplied for parameter fo type "+type+" does not satisfy minimum length of "+r.getMinInclusive());
                }
                if(length>r.getMaxInclusive()) {
                    throw new ErrorInCommand("Value "+argumentValue+" supplied for parameter fo type "+type+" does not satisfy maximum length of "+r.getMaxInclusive());
                }
            }

        } else if (type instanceof BinaryArgumentType) {
            byte[] b = StringConverter.hexStringToArray(argumentValue);
            v = ValueUtility.getBinaryValue(b);
        } else if (type instanceof EnumeratedArgumentType) {
            EnumeratedArgumentType enumType = (EnumeratedArgumentType)type;
            List<ValueEnumeration> vlist = enumType.getValueEnumerationList();
            boolean found =false;
            for(ValueEnumeration ve:vlist) {
                if(ve.getLabel().equals(argumentValue)) {
                    found = true;
                }
            }
            if(!found) {
                throw new ErrorInCommand("Value '"+argumentValue+"' supplied for enumeration argument cannot be found in enumeration list "+vlist);
            }
            v = ValueUtility.getStringValue(argumentValue);
        } else if (type instanceof BooleanArgumentType) {
            boolean b = Boolean.parseBoolean(argumentValue);
            v = ValueUtility.getBooleanValue(b);
        } else {
            throw new IllegalArgumentException("Cannot parse values of type "+type);
        }
        return v;
    }

}
