package org.yamcs.xtceproc;

import java.util.Collection;

import org.yamcs.AlarmReporter;
import org.yamcs.ParameterValue;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.xtce.AlarmLevels;
import org.yamcs.xtce.AlarmRanges;
import org.yamcs.xtce.AlarmType;
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

/**
 * Part of the TM processing chain. Is called upon by the
 * ParameterRequestManager whenever a new parameter value may need alarms
 * published together with it.
 */
public class AlarmChecker {
    
    private AlarmReporter alarmReporter;
    
    /**
     * Updates the supplied ParameterValues with monitoring (out of limits)
     * information. Any pvals that are required for performing comparison
     * matches are taken from the same set of pvals.
     */
    public void performAlarmChecking(Collection<ParameterValue> pvals) {
	
        ComparisonProcessor comparisonProcessor=new ComparisonProcessor(pvals);
        for(ParameterValue pval:pvals) {
            if(pval.getParameter().getParameterType()!=null && pval.getParameter().getParameterType().hasAlarm()) {
                performAlarmChecking(pval, comparisonProcessor);
            } else {
                pval.setMonitoringResult(MonitoringResult.IN_LIMITS);
            }
        }
    }
    
    public void enableReporting(AlarmReporter reporter) {
        this.alarmReporter=reporter;
    }
    
    /**
     * Updates the ParameterValue with monitoring (out of limits) information
     */
    private void performAlarmChecking(ParameterValue pv, ComparisonProcessor comparisonProcessor) {
        ParameterType ptype=pv.getParameter().getParameterType();
        if(ptype instanceof FloatParameterType) {
            performAlarmCheckingFloat((FloatParameterType) ptype, pv, comparisonProcessor);
        } else if(ptype instanceof EnumeratedParameterType) {
            performAlarmCheckingEnumerated((EnumeratedParameterType) ptype, pv, comparisonProcessor);
        } else if(ptype instanceof IntegerParameterType) {
            performAlarmCheckingInteger((IntegerParameterType) ptype, pv, comparisonProcessor);
        }
    }
    
    private void performAlarmCheckingInteger(IntegerParameterType ipt, ParameterValue pv, ComparisonProcessor comparisonProcessor) {
        long intCalValue=0;
        if(pv.getEngValue().hasSint32Value()) {
            intCalValue=pv.getEngValue().getSint32Value();
        } else if(pv.getEngValue().hasUint32Value()) { 
            intCalValue=0xFFFFFFFFL & pv.getEngValue().getUint32Value();
        } else if(pv.getEngValue().hasSint64Value()) {
            intCalValue=pv.getEngValue().getSint64Value();
        } else if(pv.getEngValue().hasUint64Value()) {
            intCalValue=pv.getEngValue().getUint64Value();
        } else {
            throw new IllegalStateException("Unexpected integer value");
        }
        
        // Determine applicable ranges based on context
        boolean mon=false;
        AlarmType alarmType=null;
        AlarmRanges staticAlarmRanges=null;
        int minViolations=1;
        if(ipt.getContextAlarmList()!=null) {
            for(NumericContextAlarm nca:ipt.getContextAlarmList()) {
                if(comparisonProcessor.matches(nca.getContextMatch())) {
                    mon=true;
                    alarmType=nca;
                    staticAlarmRanges=nca.getStaticAlarmRanges();
                    minViolations=nca.getMinViolations();
                    break;
                }
            }
        }
        NumericAlarm defaultAlarm=ipt.getDefaultAlarm();
        if(!mon && defaultAlarm!=null) {
            alarmType=defaultAlarm;
            staticAlarmRanges=defaultAlarm.getStaticAlarmRanges();
            minViolations=defaultAlarm.getMinViolations();
        }
        
        // Set MonitoringResult
        pv.setMonitoringResult(null); // The default is DISABLED, but set it to null, so that below code is more readable
        if(staticAlarmRanges!=null) {
            checkStaticAlarmRanges(pv, intCalValue, staticAlarmRanges);
        }
        if(pv.getMonitoringResult()==null) {
            pv.setMonitoringResult(MonitoringResult.IN_LIMITS);
        }
        // Notify when severity changes
        if(alarmReporter!=null) {
            alarmReporter.reportNumericParameterEvent(pv, alarmType, minViolations);
        }
    }

    private void performAlarmCheckingFloat(FloatParameterType fpt, ParameterValue pv, ComparisonProcessor comparisonProcessor) {
        double doubleCalValue=0;
        if(pv.getEngValue().hasFloatValue()) {
            doubleCalValue=pv.getEngValue().getFloatValue();
        } else if(pv.getEngValue().hasDoubleValue()) {
            doubleCalValue=pv.getEngValue().getDoubleValue();
        } else {
            throw new IllegalStateException("Unexpected float value");
        }

        // Determine applicable AlarmType based on context
        boolean mon=false;
        AlarmType alarmType=null;
        AlarmRanges staticAlarmRanges=null;
        int minViolations=1;
        if(fpt.getContextAlarmList()!=null) {
            for(NumericContextAlarm nca:fpt.getContextAlarmList()) {
                if(comparisonProcessor.matches(nca.getContextMatch())) {
                    mon=true;
                    alarmType=nca;
                    staticAlarmRanges=nca.getStaticAlarmRanges();
                    minViolations=nca.getMinViolations();
                    break;
                }
            }
        }
        NumericAlarm defaultAlarm=fpt.getDefaultAlarm();
        if(!mon && defaultAlarm!=null) {
            alarmType=defaultAlarm;
            staticAlarmRanges=defaultAlarm.getStaticAlarmRanges();
            minViolations=defaultAlarm.getMinViolations();
        }
        
        // Set MonitoringResult
        pv.setMonitoringResult(null); // The default is DISABLED, but set it to null, so that below code is more readable
        if(staticAlarmRanges!=null) {
            checkStaticAlarmRanges(pv, doubleCalValue, staticAlarmRanges);
        }
        if(pv.getMonitoringResult()==null) {
            pv.setMonitoringResult(MonitoringResult.IN_LIMITS);
        }
        
        // Notify when severity changes
        if(alarmReporter!=null) {
            alarmReporter.reportNumericParameterEvent(pv, alarmType, minViolations);
        }
    }
    
    /**
     * Verify limits, giving priority to highest severity
     */
    private void checkStaticAlarmRanges(ParameterValue pv, double doubleCalValue, AlarmRanges staticAlarmRanges) {
        FloatRange watchRange=staticAlarmRanges.getWatchRange();
        FloatRange warningRange=staticAlarmRanges.getWarningRange();
        FloatRange distressRange=staticAlarmRanges.getDistressRange();
        FloatRange criticalRange=staticAlarmRanges.getCriticalRange();
        FloatRange severeRange=staticAlarmRanges.getSevereRange();
        if(severeRange!=null) {
            if(severeRange.getMinInclusive()>doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.SEVERE_LOW);
            } else if(severeRange.getMaxInclusive()<doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.SEVERE_HIGH);
            }
        }
        if(pv.getMonitoringResult()==null && criticalRange!=null) {
            if(criticalRange.getMinInclusive()>doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.CRITICAL_LOW);
            } else if(criticalRange.getMaxInclusive()<doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.CRITICAL_HIGH);
            }
        }
        if(pv.getMonitoringResult()==null && distressRange!=null) {
            if(distressRange.getMinInclusive()>doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.DISTRESS_LOW);
            } else if(distressRange.getMaxInclusive()<doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.DISTRESS_HIGH);
            }
        }
        if(pv.getMonitoringResult()==null && warningRange!=null) {
            if(warningRange.getMinInclusive()>doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.WARNING_LOW);
            } else if(warningRange.getMaxInclusive()<doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.WARNING_HIGH);
            }
        }
        if(pv.getMonitoringResult()==null && watchRange!=null) {
            if(watchRange.getMinInclusive()>doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.WATCH_LOW);
            } else if(watchRange.getMaxInclusive()<doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.WATCH_HIGH);
            }
        }
        
        pv.setWatchRange(watchRange);
        pv.setWarningRange(warningRange);
        pv.setDistressRange(distressRange);
        pv.setCriticalRange(criticalRange);
        pv.setSevereRange(severeRange);
    }
    
    private void performAlarmCheckingEnumerated(EnumeratedParameterType ept, ParameterValue pv, ComparisonProcessor comparisonProcessor) {
        pv.setMonitoringResult(MonitoringResult.IN_LIMITS); // Default is DISABLED, but that doesn't seem fit when we are checking
        String s=pv.getEngValue().getStringValue();
        
        EnumerationAlarm alarm=ept.getDefaultAlarm();
        int minViolations=(alarm==null)?1:alarm.getMinViolations();
        if(ept.getContextAlarmList()!=null) {
            for(EnumerationContextAlarm nca:ept.getContextAlarmList()) {
                if(comparisonProcessor.matches(nca.getContextMatch())) {
                    alarm=nca;
                    minViolations=nca.getMinViolations();
                    break;
                }
            }
        }
        if(alarm != null) {
            AlarmLevels level=alarm.getDefaultAlarmLevel();
            for(EnumerationAlarmItem eai:alarm.getAlarmList()) {
                if(eai.getEnumerationValue().getLabel().equals(s)) level=eai.getAlarmLevel();
            }

            switch(level) {
            case normal:
                pv.setMonitoringResult(MonitoringResult.IN_LIMITS);
                break;
            case watch:
                pv.setMonitoringResult(MonitoringResult.WATCH);
                break;
            case warning:
                pv.setMonitoringResult(MonitoringResult.WARNING);
                break;
            case distress:
                pv.setMonitoringResult(MonitoringResult.DISTRESS);
                break;
            case critical:
                pv.setMonitoringResult(MonitoringResult.CRITICAL);
                break;
            case severe:
                pv.setMonitoringResult(MonitoringResult.SEVERE);
                break;
            }
        }

        if(alarmReporter!=null) {
            alarmReporter.reportEnumeratedParameterEvent(pv, alarm, minViolations);
        }
    }
}
