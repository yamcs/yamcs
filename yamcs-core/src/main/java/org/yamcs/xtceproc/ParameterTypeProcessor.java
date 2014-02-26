package org.yamcs.xtceproc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ParameterValue;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.xtce.AlarmLevels;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.EnumerationAlarm;
import org.yamcs.xtce.EnumerationAlarm.EnumerationAlarmItem;
import org.yamcs.xtce.EnumerationContextAlarm;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.FloatRange;
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
    	if(ptype instanceof EnumeratedParameterType) {
    		return extractEnumerated((EnumeratedParameterType) ptype);
    	} else if(ptype instanceof IntegerParameterType) {
    		return extractInteger((IntegerParameterType) ptype);
    	} else if(ptype instanceof FloatParameterType) {
    		return extractFloat((FloatParameterType) ptype);
    	} else if(ptype instanceof BinaryParameterType) {
    		return extractBinary((BinaryParameterType) ptype);
    	} else if(ptype instanceof StringParameterType) {
            return extractString((StringParameterType) ptype);
        } else if(ptype instanceof BooleanParameterType) {
    		return extractBoolean((BooleanParameterType) ptype);
    	}  
    	log.error("Extraction of "+ptype+" not implemented");
    	throw new RuntimeException("Extraction of "+ptype+" not implemented");
    }


    private ParameterValue extractFloat(FloatParameterType fpt) {
        ParameterValue pv=new ParameterValue(null,true);
        pv.setAbsoluteBitOffset(pcontext.containerAbsoluteByteOffset*8+pcontext.bitPosition);
        pv.setBitSize(fpt.getEncoding().getSizeInBits());
        pcontext.dataEncodingProcessor.extractRaw(fpt.getEncoding(), pv);
        return pv;
    }

    private ParameterValue extractBoolean(BooleanParameterType bpt) {
        ParameterValue pv = new ParameterValue(null,true);
        pv.setAbsoluteBitOffset(pcontext.containerAbsoluteByteOffset*8+pcontext.bitPosition);
        pv.setBitSize(bpt.getEncoding().getSizeInBits());
        pcontext.dataEncodingProcessor.extractRaw(bpt.getEncoding(), pv);

        //check if it out of range
        return pv;
    }

    private ParameterValue extractBinary(BinaryParameterType bpt) {
        ParameterValue pv=new ParameterValue(null,true);
        pv.setAbsoluteBitOffset(pcontext.containerAbsoluteByteOffset*8+pcontext.bitPosition);
        pv.setBitSize(bpt.getEncoding().getSizeInBits());
        pcontext.dataEncodingProcessor.extractRaw(bpt.getEncoding(), pv);
        return pv;
    }

    private ParameterValue extractString(StringParameterType spt) {
        ParameterValue pv=new ParameterValue(null,true);
        pv.setAbsoluteBitOffset(pcontext.containerAbsoluteByteOffset*8+pcontext.bitPosition);
        pv.setBitSize(spt.getEncoding().getSizeInBits());
        pcontext.dataEncodingProcessor.extractRaw(spt.getEncoding(), pv);
        return pv;
    }
    
    private ParameterValue extractEnumerated(EnumeratedParameterType ept) {
        ParameterValue pv=new ParameterValue(null,true);
        pv.setAbsoluteBitOffset(pcontext.containerAbsoluteByteOffset*8+pcontext.bitPosition);
        pv.setBitSize(ept.getEncoding().getSizeInBits());
        pcontext.dataEncodingProcessor.extractRaw(ept.getEncoding(), pv);
        return pv;
    }

    private ParameterValue extractInteger(IntegerParameterType ipt) {
        ParameterValue pv=new ParameterValue(null,true);
        pv.setAbsoluteBitOffset(pcontext.containerAbsoluteByteOffset*8+pcontext.bitPosition);
        pv.setBitSize(ipt.getEncoding().getSizeInBits());
        pcontext.dataEncodingProcessor.extractRaw(ipt.getEncoding(), pv);

        //check if it out of range
        return pv;
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
