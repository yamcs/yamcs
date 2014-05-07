package org.yamcs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.AlarmType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.AbstractService;

/**
 * Generates realtime alarm events automatically, by subscribing to all relevant
 * parameters.
 * <p>
 * <b>Must be declared after {@link YarchChannel}</b>
 */
public class AlarmReporter extends AbstractService implements ParameterConsumer {
    
    private EventProducer eventProducer;
    private Map<Parameter, ActiveAlarm> activeAlarms=new HashMap<Parameter, ActiveAlarm>();
    
    public AlarmReporter(String yamcsInstance) {
        this(yamcsInstance, "realtime");
    }
    
    public AlarmReporter(String yamcsInstance, String channelName) {
        eventProducer=EventProducerFactory.getEventProducer(yamcsInstance);
        eventProducer.setSource("AlarmChecker");
        
        Channel channel = Channel.getInstance(yamcsInstance, channelName);
        ParameterRequestManager prm = channel.getParameterRequestManager();
        prm.getAlarmChecker().enableReporting(this);
        
        // Auto-subscribe to parameters with alarms
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
    public void doStart() {
        notifyStarted();
    }
    
    @Override
    public void doStop() {
        notifyStopped();
    }
    
    @Override
    public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> items) {
        // Nothing. The real business of sending events, happens while checking the alarms
        // because that's where we have easy access to the XTCE definition of the active
        // alarm. The PRM is only used to signal the parameter subscriptions.
    }

    /**
     * Sends an event if an alarm condition for the active context has been
     * triggered <tt>minViolations</tt> times. This configuration does not
     * affect events for parameters that go back to normal, or that change
     * severity levels while the alarm is already active.
     */
    public void sendNumericParameterEvent(ParameterValue pv, AlarmType alarmType, int minViolations) {
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
    
    public void sendEnumeratedParameterEvent(ParameterValue pv, AlarmType alarmType, int minViolations) {
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
