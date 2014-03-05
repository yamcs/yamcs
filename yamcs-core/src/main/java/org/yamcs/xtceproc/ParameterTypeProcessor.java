package org.yamcs.xtceproc;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ParameterValue;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.AlarmLevels;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.EnumerationAlarm;
import org.yamcs.xtce.EnumerationAlarm.EnumerationAlarmItem;
import org.yamcs.xtce.EnumerationContextAlarm;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.FloatRange;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.NumericAlarm;
import org.yamcs.xtce.NumericContextAlarm;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.StringParameterType;


public class ParameterTypeProcessor {
    ProcessingContext pcontext;
    Logger log=LoggerFactory.getLogger(this.getClass().getName());

    ParameterTypeProcessor(ProcessingContext pcontext) {
        this.pcontext=pcontext;
    }

    /**
     *  Extracts the parameter from the packet.
     * @return value of the parameter after extraction
     */
    public ParameterValue extract(ParameterType ptype) {
        ParameterValue pv=new ParameterValue(null,true);
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
            doIntegerCalibration(ipt, pval, rawValue.getUint32Value());
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

    /**
     * Updates the ParameterValue with monitoring (out of limits) information
     * 
     */
    public void performLimitChecking(ParameterType ptype, ParameterValue pv) {
        if(ptype instanceof FloatParameterType) {
            performLimitCheckingFloat((FloatParameterType) ptype, pv);
        } else if(ptype instanceof EnumeratedParameterType) {
            performLimitCheckingEnumerated((EnumeratedParameterType) ptype, pv);
        } else if(ptype instanceof IntegerParameterType) {
            performLimitCheckingInteger((IntegerParameterType) ptype, pv);
        }
    }

    private void performLimitCheckingFloat(FloatParameterType fpt,   ParameterValue pv) {
        FloatRange criticalRange=null;
        FloatRange warningRange=null;

        double doubleCalValue=0;

        if(fpt.getSizeInBits()==32) {
            doubleCalValue=pv.getEngValue().getFloatValue();
        } else {
            doubleCalValue=pv.getEngValue().getDoubleValue();
        }
        boolean mon=false;
        if(fpt.getContextAlarmList()!=null) {
            for(NumericContextAlarm nca:fpt.getContextAlarmList()) {
                if(pcontext.comparisonProcessor.matches(nca.getContextMatch())) {
                    mon=true;
                    criticalRange=nca.getStaticAlarmRanges().getCriticalRange();
                    warningRange=nca.getStaticAlarmRanges().getWarningRange();
                    break;
                }
            }
        }
        NumericAlarm defaultAlarm=fpt.getDefaultAlarm();
        if((!mon) && (defaultAlarm!=null)) {
            criticalRange=defaultAlarm.getStaticAlarmRanges().getCriticalRange();
            warningRange=defaultAlarm.getStaticAlarmRanges().getWarningRange();
        }
        boolean critical=false;
        if(criticalRange!=null) {
            if(criticalRange.getMinInclusive()>doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.DANGER_LOW_LIMIT_VIOLATION);
                critical=true;
            } else if(criticalRange.getMaxInclusive()<doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.DANGER_HIGH_LIMIT_VIOLATION);
                critical=true;
            } else {
                pv.setMonitoringResult(MonitoringResult.IN_LIMITS);
            }
        }
        if(!critical && (warningRange!=null)) {
            if(warningRange.getMinInclusive()>doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.NOMINAL_LOW_LIMIT_VIOLATION);
            } else if(warningRange.getMaxInclusive()<doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.NOMINAL_HIGH_LIMIT_VIOLATION);
            } else {
                pv.setMonitoringResult(MonitoringResult.IN_LIMITS);
            }
        }
        pv.setCriticalRange(criticalRange);
        pv.setWarningRange(warningRange);

    }

    private void performLimitCheckingInteger(IntegerParameterType ipt, ParameterValue pv) {
        long intCalValue=0;
        
        if(ipt.isSigned())
            intCalValue=pv.getEngValue().getSint32Value();
        else 
            intCalValue=0xFFFFFFFFL & pv.getEngValue().getUint32Value();
        
        FloatRange criticalRange=null;
        FloatRange warningRange=null;

        boolean mon=false;
        if(ipt.getContextAlarmList()!=null) {
            for(NumericContextAlarm nca:ipt.getContextAlarmList()) {
                if(pcontext.comparisonProcessor.matches(nca.getContextMatch())) {
                    mon=true;
                    criticalRange=nca.getStaticAlarmRanges().getCriticalRange();
                    warningRange=nca.getStaticAlarmRanges().getWarningRange();
                    break;
                }
            }
        }
        
        if((!mon) && (ipt.getDefaultAlarm()!=null)) {
            criticalRange=ipt.getDefaultAlarm().getStaticAlarmRanges().getCriticalRange();
            warningRange=ipt.getDefaultAlarm().getStaticAlarmRanges().getWarningRange();
        }
        pv.setCriticalRange(criticalRange);
        pv.setWarningRange(warningRange);
        
        boolean critical=false;
        if(criticalRange!=null) {
            if(criticalRange.getMinInclusive()>intCalValue) {
                pv.setMonitoringResult(MonitoringResult.DANGER_LOW_LIMIT_VIOLATION);
                critical=true;
            } else if(criticalRange.getMaxInclusive()<intCalValue) {
                pv.setMonitoringResult(MonitoringResult.DANGER_HIGH_LIMIT_VIOLATION);
                critical=true;
            }
        }
        if(!critical && (warningRange!=null)) {
            if(warningRange.getMinInclusive()>intCalValue) {
                pv.setMonitoringResult(MonitoringResult.NOMINAL_LOW_LIMIT_VIOLATION);
                critical=true;
            } else if(warningRange.getMaxInclusive()<intCalValue) {
                pv.setMonitoringResult(MonitoringResult.NOMINAL_HIGH_LIMIT_VIOLATION);
                critical=true;
            }
        }
        
    }
    
    public void performLimitCheckingEnumerated(EnumeratedParameterType ept, ParameterValue pv) {

        String s=pv.getEngValue().getStringValue();
        
        EnumerationAlarm alarm=ept.getDefaultAlarm();
        if(ept.getContextAlarmList()!=null) {
            for(EnumerationContextAlarm nca:ept.getContextAlarmList()) {
                if(pcontext.comparisonProcessor.matches(nca.getContextMatch())) {
                    alarm=nca;
                    break;
                }
            }
        }
        
        
        if(alarm!=null) {
            AlarmLevels level=alarm.getDefaultAlarmLevel();
            for(EnumerationAlarmItem eai:alarm.getAlarmList()) {
                if(eai.getEnumerationValue().equals(s)) level=eai.getAlarmLevel();
            }
            switch(level) {
            case normal:
                pv.setMonitoringResult(MonitoringResult.IN_LIMITS);
                break;
            case warning:
                pv.setMonitoringResult(MonitoringResult.NOMINAL_LIMIT_VIOLATION);
                break;
            case crtical:
                pv.setMonitoringResult(MonitoringResult.DANGER_HIGH_LIMIT_VIOLATION); //there is no danger_limit_violation so we use this one
                break;
            }
        }
        
    }


}
