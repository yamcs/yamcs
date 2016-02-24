package org.yamcs.alarms;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.util.concurrent.AbstractService;

/**
 * Maintains a list of active alarms. 
 */
public class AlarmServer extends AbstractService {

    private EventProducer eventProducer;
    private Map<Parameter, ActiveAlarm> activeAlarms=new ConcurrentHashMap<>();
    // Last value of each param (for detecting changes in value)
    final String yamcsInstance;
    private final Logger log = LoggerFactory.getLogger(AlarmServer.class);
    private Set<AlarmListener> alarmListeners = new HashSet<>();
    private String streamName;
    
    static public final TupleDefinition ALARM_TUPLE_DEFINITION=new TupleDefinition();
    //user time, parameter name sequence number and event 
    static {
        ALARM_TUPLE_DEFINITION.addColumn("triggerTime", DataType.TIMESTAMP);
        ALARM_TUPLE_DEFINITION.addColumn("parameter", DataType.STRING);
        ALARM_TUPLE_DEFINITION.addColumn("seqNum", DataType.INT);
        ALARM_TUPLE_DEFINITION.addColumn("event", DataType.STRING);
    }
    
    /**
     * alarm server without a listener (used for unit tests )
     * @param yamcsInstance
     */
    AlarmServer(String yamcsInstance) {
        this.yamcsInstance = yamcsInstance;
        eventProducer=EventProducerFactory.getEventProducer(yamcsInstance);
        eventProducer.setSource("AlarmServer");
    }

    /**
     * Creates an alarm server that pushes all alarms to a stream
     * @param yamcsInstance
     * @param streamName
     */
    public AlarmServer(String yamcsInstance, String streamName) {
        this.yamcsInstance = yamcsInstance;
        eventProducer=EventProducerFactory.getEventProducer(yamcsInstance);
        eventProducer.setSource("AlarmServer");
        this.streamName = streamName;
    }
    
    /**
     * Register for alarm notices
     * @return the current set of active alarms
     */
    public Map<Parameter, ActiveAlarm> subscribe(AlarmListener listener) {
        alarmListeners.add(listener);
        return activeAlarms;
    }
    
    public void unsubscribe(AlarmListener listener) {
        alarmListeners.remove(listener);
    }
    
    /**
     * Returns the current set of active alarms
     */
    public Map<Parameter, ActiveAlarm> getActiveAlarms() {
        return activeAlarms;
    }

    @Override
    public void doStart() {
        if(streamName!=null) {
            YarchDatabase ydb = YarchDatabase.getInstance(yamcsInstance);
            Stream s = ydb.getStream(streamName);
            if(s==null) {
                notifyFailed(new ConfigurationException("Cannot find a stream named '"+streamName+"'"));
                return;
            }
            subscribe(new AlarmStreamer(s));
        }
        notifyStarted();
    }

    @Override
    public void doStop() {
        notifyStopped();
    }

    public void update(ParameterValue pv, int minViolations) {
        update(pv, minViolations, false);
    }
    
    public void update(ParameterValue pv, int minViolations, boolean autoAck) {
        Parameter param = pv.getParameter();
        
        ActiveAlarm activeAlarm=activeAlarms.get(pv.getParameter());
        
        if((activeAlarm==null) && (pv.getMonitoringResult()==MonitoringResult.IN_LIMITS
        || pv.getMonitoringResult() == null
                || pv.getMonitoringResult() == MonitoringResult.DISABLED)) {
            return;
        }
        
        
        if(pv.getMonitoringResult()==MonitoringResult.IN_LIMITS
                || pv.getMonitoringResult() == MonitoringResult.DISABLED
                || pv.getMonitoringResult() == null) {

            if (activeAlarm.violations < minViolations) {
                log.debug("Clearing glitch for {}", param.getQualifiedName());
                activeAlarms.remove(param);
                return;
            }

            activeAlarm.currentValue = pv;
            if ((activeAlarm.acknowledged) || (activeAlarm.autoAcknowledge)) {
                for (AlarmListener l : alarmListeners) {
                    l.notifyCleared(activeAlarm);
                }
                activeAlarms.remove(pv.getParameter());
            } else {
                for (AlarmListener l : alarmListeners) {
                    l.notifyParameterValueUpdate(activeAlarm);                    
                }
            }

        } else { // out of limits
            if(activeAlarm==null) {
                activeAlarm=new ActiveAlarm(pv, autoAck);
                activeAlarms.put(pv.getParameter(), activeAlarm);
            } else {
                activeAlarm.currentValue = pv;
                activeAlarm.violations++;
            }
            if(activeAlarm.violations < minViolations) {
                return;
            }
        
            if(activeAlarm.violations == minViolations) {
                for (AlarmListener l : alarmListeners) {
                    l.notifyTriggered(activeAlarm);                    
                }
            } else {
                if(moreSevere(pv.getMonitoringResult(), activeAlarm.mostSevereValue.getMonitoringResult())){
                    activeAlarm.mostSevereValue = pv;
                    for (AlarmListener l : alarmListeners) {
                        l.notifySeverityIncrease(activeAlarm);                        
                    }
                }
                for (AlarmListener l : alarmListeners) {
                    l.notifyParameterValueUpdate(activeAlarm);                        
                }
            }
        
            activeAlarms.put(pv.getParameter(), activeAlarm);
        }
    }

    public ActiveAlarm acknowledge(Parameter p, int id, String username, long ackTime, String message) throws CouldNotAcknowledgeAlarmException {
        ActiveAlarm aa = activeAlarms.get(p);
        if(aa==null) {
            throw new CouldNotAcknowledgeAlarmException("Parameter " + p.getQualifiedName() + " is not in state of alarm");
        }
        if(aa.id!=id) {
            log.warn("Got acknowledge for parameter "+p+" but the id does not match");
            throw new CouldNotAcknowledgeAlarmException("Alarm Id " + id + " does not match parameter " + p.getQualifiedName());
        }
        
        aa.acknowledged = true;
        aa.usernameThatAcknowledged = username;
        aa.acknowledgeTime = ackTime;
        aa.message = message;
        alarmListeners.forEach(l -> l.notifyAcknowledged(aa));
        
        if(aa.currentValue.getMonitoringResult()==MonitoringResult.IN_LIMITS
                || aa.currentValue.getMonitoringResult()==MonitoringResult.DISABLED
                || aa.currentValue.getMonitoringResult()==null) {
            
            activeAlarms.remove(p);
            alarmListeners.forEach(l -> l.notifyCleared(aa));
        }
        
        return aa;
    }

    protected static boolean moreSevere(MonitoringResult mr1, MonitoringResult mr2) {
        if (mr1 == mr2) return false;
        switch (mr2) {
        case WATCH:
            if (mr1 == MonitoringResult.WARNING) {
                return true;
            }
            // fall
        case WARNING:
            if (mr1 == MonitoringResult.DISTRESS) {
                return true;
            }
            // fall
        case DISTRESS:
            if (mr1 == MonitoringResult.CRITICAL) {
                return true;
            }
            // fall
        case CRITICAL:
            if (mr1 == MonitoringResult.SEVERE) {
                return true;
            }
        default:
            return false;
        }
    }
}
