package org.yamcs.xtceproc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.ParameterConsumer;
import org.yamcs.ParameterRequestManager;
import org.yamcs.ParameterValue;
import org.yamcs.ParameterValueWithId;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
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
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.XtceDb;

/**
 * Is called upon by the ParameterRequestManager whenever a new parameter value
 * may need alarms published together with it.
 * <p>
 * Also generates alarm events automatically, by subscribing to all relevant
 * parameters.
 */
public class AlarmChecker implements ParameterConsumer {
    
    private EventProducer eventProducer;
    private Map<Parameter, ActiveAlarm> activeAlarms=new HashMap<Parameter, ActiveAlarm>();
    
    public AlarmChecker(ParameterRequestManager prm, String yamcsInstance) {
        eventProducer=EventProducerFactory.getEventProducer(yamcsInstance);
        eventProducer.setSource("AlarmChecker");
        
        Set<Parameter> requiredParameters=new HashSet<Parameter>();
        try {
            XtceDb xtcedb=XtceDbFactory.getInstance(yamcsInstance);
            for (Parameter parameter:xtcedb.getParameters()) {
                ParameterType ptype=parameter.getParameterType();
                if(ptype.hasAlarm()) {
                    requiredParameters.add(parameter);
                    Set<Parameter> dependentParameters = ptype.getDependentParameters();
                    if(dependentParameters!=null) {
                        requiredParameters.addAll(dependentParameters);
                    }
                }
            }
        } catch(ConfigurationException e) {
            throw new RuntimeException(e);
        }
        
        if(!requiredParameters.isEmpty()) {
            List<NamedObjectId> paramNames=new ArrayList<NamedObjectId>(); // Now that we have uniques..
            for(Parameter p:requiredParameters) {
                paramNames.add(NamedObjectId.newBuilder().setName(p.getQualifiedName()).build());
            }
            try {
                prm.addRequest(paramNames, this);
            } catch(InvalidIdentification e) {
                throw new RuntimeException("Could not register dependencies for alarms", e);
            }
        }
    }
    
    @Override
    public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
        // Nothing. The real business of sending events, happens while checking the alarms
        // because that's where we have easy access to the XTCE definition of the active
        // alarm. The PRM is only used to signal the parameter subscriptions.
    }
       
    /**
     * Updates the supplied ParameterValues with monitoring (out of limits)
     * information. Any pvals that are required for performing comparison
     * matches are taken from the same set of pvals.
     */
    public void performAlarmChecking(Collection<ParameterValue> pvals) {
        ComparisonProcessor comparisonProcessor=new ComparisonProcessor(pvals);
        for(ParameterValue pval:pvals) {
            performAlarmChecking(pval, comparisonProcessor);
        }
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
        sendNumericParameterEvent(pv, alarmType, minViolations);
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
        sendNumericParameterEvent(pv, alarmType, minViolations);
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
        
        sendEnumeratedParameterEvent(pv, alarm, minViolations);
    }

    /**
     * Sends an event if an alarm condition for the active context has been
     * triggered <tt>minViolations</tt> times. This configuration does not
     * affect events for parameters that go back to normal, or that change
     * severity levels while the alarm is already active.
     */
    private void sendNumericParameterEvent(ParameterValue pv, AlarmType alarmType, int minViolations) {
        if(pv.getMonitoringResult()==MonitoringResult.IN_LIMITS) {
            if(activeAlarms.containsKey(pv.getParameter())) {
                eventProducer.sendInfo("NORMAL", "Parameter "+pv.getParameter().getQualifiedName()+" is back to normal");
                activeAlarms.remove(pv.getParameter());
            }
        } else { // out of limits
            MonitoringResult previousMonitoringResult=null;
            ActiveAlarm activeAlarm=activeAlarms.get(pv.getParameter());
            if(activeAlarm==null || activeAlarm.alarmType!=alarmType) {
                activeAlarm=new ActiveAlarm(alarmType, pv.getMonitoringResult());
            } else {
                previousMonitoringResult=activeAlarm.monitoringResult;
                activeAlarm.monitoringResult=pv.getMonitoringResult();
                activeAlarm.violations++;
            }
            
            if(activeAlarm.violations==minViolations || (activeAlarm.violations>minViolations && previousMonitoringResult!=activeAlarm.monitoringResult)) {
                switch(pv.getMonitoringResult()) {
                case WATCH_LOW:
                case WARNING_LOW:
                    eventProducer.sendWarning(pv.getMonitoringResult().toString(), "Parameter "+pv.getParameter().getQualifiedName()+" is too low");
                    break;
                case WATCH_HIGH:
                case WARNING_HIGH:
                    eventProducer.sendWarning(pv.getMonitoringResult().toString(), "Parameter "+pv.getParameter().getQualifiedName()+" is too high");
                    break;
                case DISTRESS_LOW:
                case CRITICAL_LOW:
                case SEVERE_LOW:
                    eventProducer.sendError(pv.getMonitoringResult().toString(), "Parameter "+pv.getParameter().getQualifiedName()+" is too low");
                    break;
                case DISTRESS_HIGH:
                case CRITICAL_HIGH:
                case SEVERE_HIGH:
                    eventProducer.sendError(pv.getMonitoringResult().toString(), "Parameter "+pv.getParameter().getQualifiedName()+" is too high");
                    break;
                default:
                    throw new IllegalStateException("Unexpected monitoring result: "+pv.getMonitoringResult());
                }
            }
            
            activeAlarms.put(pv.getParameter(), activeAlarm);
        }
    }
    
    private void sendEnumeratedParameterEvent(ParameterValue pv, AlarmType alarmType, int minViolations) {
        if(pv.getMonitoringResult()==MonitoringResult.IN_LIMITS) {
            if(activeAlarms.containsKey(pv.getParameter())) {
                eventProducer.sendInfo("NORMAL", "Parameter "+pv.getParameter().getQualifiedName()+" is back to a normal state ("+pv.getEngValue().getStringValue()+")");
                activeAlarms.remove(pv.getParameter());
            }
        } else { // out of limits
            MonitoringResult previousMonitoringResult=null;
            ActiveAlarm activeAlarm=activeAlarms.get(pv.getParameter());
            if(activeAlarm==null || activeAlarm.alarmType!=alarmType) {
                activeAlarm=new ActiveAlarm(alarmType, pv.getMonitoringResult());
            } else {
                previousMonitoringResult=activeAlarm.monitoringResult;
                activeAlarm.monitoringResult=pv.getMonitoringResult();
                activeAlarm.violations++;
            }
            
            if(activeAlarm.violations==minViolations || (activeAlarm.violations>minViolations&& previousMonitoringResult!=activeAlarm.monitoringResult)) {
                switch(pv.getMonitoringResult()) {
                case WATCH:
                case WARNING:
                    eventProducer.sendWarning(pv.getMonitoringResult().toString(), "Parameter "+pv.getParameter().getQualifiedName()+" transitioned to state "+pv.getEngValue().getStringValue());
                    break;
                case DISTRESS:
                case CRITICAL:
                case SEVERE:
                    eventProducer.sendError(pv.getMonitoringResult().toString(), "Parameter "+pv.getParameter().getQualifiedName()+" transitioned to state "+pv.getEngValue().getStringValue());
                    break;
                default:
                    throw new IllegalStateException("Unexpected monitoring result: "+pv.getMonitoringResult());
                }
            }
            
            activeAlarms.put(pv.getParameter(), activeAlarm);
        }
    }
    
    private static class ActiveAlarm {
        MonitoringResult monitoringResult;
        AlarmType alarmType;
        int violations=1;
        ActiveAlarm(AlarmType alarmType, MonitoringResult monitoringResult) {
            this.alarmType=alarmType;
            this.monitoringResult=monitoringResult;
        }
    }
}
