package org.yamcs.xtceproc;

import java.util.Collection;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.AlarmReporter;
import org.yamcs.InvalidIdentification;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.parameter.ParameterRequestManagerImpl;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.AlarmLevels;
import org.yamcs.xtce.AlarmRanges;
import org.yamcs.xtce.AlarmType;
import org.yamcs.xtce.CriteriaEvaluator;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.EnumerationAlarm;
import org.yamcs.xtce.EnumerationAlarm.EnumerationAlarmItem;
import org.yamcs.xtce.EnumerationContextAlarm;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.FloatRange;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.NumericAlarm;
import org.yamcs.xtce.NumericContextAlarm;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;

/**
 * Part of the TM processing chain. Is called upon by the
 * ParameterRequestManager whenever a new parameter value may need alarms
 * published together with it.
 */
public class AlarmChecker {

    private AlarmReporter alarmReporter;

    private AlarmServer alarmServer;

    private final int subscriptionId;
    ParameterRequestManagerImpl prm;
    Logger log = LoggerFactory.getLogger(this.getClass());

    //keep the last values of parameters that are needed to check alarms (for alarms that are enabled/disabled based on some other parameters)
    ParameterValueList lastValues = new ParameterValueList();


    public AlarmChecker(ParameterRequestManagerImpl prm, int subscriptionId) {
        this.subscriptionId = subscriptionId;
        this.prm = prm;
    }

    /**
     * Called from the ParameterRequestManager when a new parameter has been subscribed
     * Check and subscribe any dependencies required for alarm checking
     */
    public void parameterSubscribed(Parameter p) {
        ParameterType ptype = p.getParameterType();
        if(ptype==null) {
            log.debug("Parameter "+p.getName()+" has no type");
            return;
        }
        Set<Parameter> params = ptype.getDependentParameters();
        if(params!=null) {
            for(Parameter p1:params) {
                try {
                    prm.addItemsToRequest(subscriptionId, p1);
                } catch (InvalidIdentification e) {
                    log.warn("Got exception when subscribing for "+p1+" which is a dependency for "+p.getName());
                }
            }
        }
    }

    /**
     * Update the list of parameters used for alarm checking
     * @param pvals
     */
    public void updateParameters(Collection<ParameterValue> pvals) {
        synchronized(lastValues) {
            for(ParameterValue pv: pvals) {
                lastValues.removeFirst(pv.getParameter());
                lastValues.add(pv);
            }
        }
    }

    /**
     * Updates the supplied ParameterValues with monitoring (out of limits)
     * information.
     */
    public void performAlarmChecking(Collection<ParameterValue> pvals) {
        CriteriaEvaluator criteriaEvaluator = new CriteriaEvaluatorImpl(lastValues);
        for(ParameterValue pval:pvals) {
            if(pval.getParameter().getParameterType()!=null && pval.getParameter().getParameterType().hasAlarm()) {
                performAlarmChecking(pval, criteriaEvaluator);
            } //else do not set the MonitoringResult
        }
    }

    public void enableReporting(AlarmReporter reporter) {
        this.alarmReporter=reporter;
    }

    public void enableServer(AlarmServer server) {
        this.alarmServer = server;
    }

    /**
     * Updates the ParameterValue with monitoring (out of limits) information
     */
    private void performAlarmChecking(ParameterValue pv, CriteriaEvaluator criteriaEvaluator) {
        ParameterType ptype=pv.getParameter().getParameterType();
        if(ptype instanceof FloatParameterType) {
            performAlarmCheckingFloat((FloatParameterType) ptype, pv, criteriaEvaluator);
        } else if(ptype instanceof EnumeratedParameterType) {
            performAlarmCheckingEnumerated((EnumeratedParameterType) ptype, pv, criteriaEvaluator);
        } else if(ptype instanceof IntegerParameterType) {
            performAlarmCheckingInteger((IntegerParameterType) ptype, pv, criteriaEvaluator);
        }
    }

    private void performAlarmCheckingInteger(IntegerParameterType ipt, ParameterValue pv, CriteriaEvaluator criteriaEvaluator) {
        long intCalValue=0;
        if(pv.getEngValue().getType()==Type.SINT32) {
            intCalValue=pv.getEngValue().getSint32Value();
        } else if(pv.getEngValue().getType()==Type.UINT32) { 
            intCalValue=0xFFFFFFFFL & pv.getEngValue().getUint32Value();
        } else if(pv.getEngValue().getType()==Type.SINT64) {
            intCalValue=pv.getEngValue().getSint64Value();
        } else if(pv.getEngValue().getType()==Type.UINT64) {
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
                if(nca.getContextMatch().isMet(criteriaEvaluator)) {
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



        // Notify when severity changes
        if(alarmReporter!=null) {
            alarmReporter.reportNumericParameterEvent(pv, alarmType, minViolations);
        }
        if(alarmServer!=null) {
            alarmServer.update(pv, minViolations);
        }
    }

    private void performAlarmCheckingFloat(FloatParameterType fpt, ParameterValue pv, CriteriaEvaluator criteriaEvaluator) {
        double doubleCalValue=0;
        if(pv.getEngValue().getType()==Type.FLOAT) {
            doubleCalValue=pv.getEngValue().getFloatValue();
        } else if(pv.getEngValue().getType()==Type.DOUBLE) {
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
                if(nca.getContextMatch().isMet(criteriaEvaluator)) {
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

        // Notify when severity changes
        if(alarmReporter!=null) {
            alarmReporter.reportNumericParameterEvent(pv, alarmType, minViolations);
        }
        if(alarmServer!=null) {
            alarmServer.update(pv, minViolations);
        }
    }

    /**
     * Verify limits, giving priority to highest severity
     */
    private void checkStaticAlarmRanges(ParameterValue pv, double doubleCalValue, AlarmRanges staticAlarmRanges) {
        pv.setMonitoringResult(null);
        FloatRange watchRange=staticAlarmRanges.getWatchRange();
        FloatRange warningRange=staticAlarmRanges.getWarningRange();
        FloatRange distressRange=staticAlarmRanges.getDistressRange();
        FloatRange criticalRange=staticAlarmRanges.getCriticalRange();
        FloatRange severeRange=staticAlarmRanges.getSevereRange();
        if(severeRange!=null) {
            if(severeRange.getMinInclusive()>doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.SEVERE);
                pv.setRangeCondition(RangeCondition.LOW);
            } else if(severeRange.getMaxInclusive()<doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.SEVERE);
                pv.setRangeCondition(RangeCondition.HIGH);
            }
        }
        if(pv.getMonitoringResult()==null && criticalRange!=null) {
            if(criticalRange.getMinInclusive()>doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.CRITICAL);
                pv.setRangeCondition(RangeCondition.LOW);
            } else if(criticalRange.getMaxInclusive()<doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.CRITICAL);
                pv.setRangeCondition(RangeCondition.HIGH);
            }
        }
        if(pv.getMonitoringResult()==null && distressRange!=null) {
            if(distressRange.getMinInclusive()>doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.DISTRESS);
                pv.setRangeCondition(RangeCondition.LOW);
            } else if(distressRange.getMaxInclusive()<doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.DISTRESS);
                pv.setRangeCondition(RangeCondition.HIGH);
            }
        }
        if(pv.getMonitoringResult()==null && warningRange!=null) {
            if(warningRange.getMinInclusive()>doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.WARNING);
                pv.setRangeCondition(RangeCondition.LOW);
            } else if(warningRange.getMaxInclusive()<doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.WARNING);
                pv.setRangeCondition(RangeCondition.HIGH);
            }
        }
        if(pv.getMonitoringResult()==null && watchRange!=null) {
            if(watchRange.getMinInclusive()>doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.WATCH);
                pv.setRangeCondition(RangeCondition.LOW);
            } else if(watchRange.getMaxInclusive()<doubleCalValue) {
                pv.setMonitoringResult(MonitoringResult.WATCH);
                pv.setRangeCondition(RangeCondition.HIGH);
            }
        }

        if (pv.getMonitoringResult() == null) {
            pv.setMonitoringResult(MonitoringResult.IN_LIMITS);
        }

        pv.setWatchRange(watchRange);
        pv.setWarningRange(warningRange);
        pv.setDistressRange(distressRange);
        pv.setCriticalRange(criticalRange);
        pv.setSevereRange(severeRange);
    }

    private void performAlarmCheckingEnumerated(EnumeratedParameterType ept, ParameterValue pv, CriteriaEvaluator criteriaEvaluator) {
        pv.setMonitoringResult(null); // Default is DISABLED, but that doesn't seem fit when we are checking
        String s=pv.getEngValue().getStringValue();

        EnumerationAlarm alarm=ept.getDefaultAlarm();
        int minViolations=(alarm==null)?1:alarm.getMinViolations();
        if(ept.getContextAlarmList()!=null) {
            for(EnumerationContextAlarm nca:ept.getContextAlarmList()) {
                if(nca.getContextMatch().isMet(criteriaEvaluator)) {
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

            if(alarmReporter!=null) {
                alarmReporter.reportEnumeratedParameterEvent(pv, alarm, minViolations);
            }
        }


        if(alarmServer!=null) {
            alarmServer.update(pv, minViolations);
        }
    }

    public int getSubscriptionId() {
        return subscriptionId;
    }
}
