package org.yamcs.alarms;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtceproc.ParameterAlarmChecker;

import com.google.common.util.concurrent.AbstractService;

/**
 * Maintains a list of active alarms.
 * <p>
 * (S,T) can be one of: (Parameter,ParamerValue) or (EventId, Event)
 * <p>
 * This class implements functionality common for parameter alarms and event alarms.
 * <p>
 * Specific functionality for each alarm type (e.g. disabling alarms) should be implemented in the respective {@link ParameterAlarmChecker} or
 * {@link EventAlarmServer}.
 * 
 */
public class AlarmServer<S, T> extends AbstractService {

    private Map<S, ActiveAlarm<T>> activeAlarms = new ConcurrentHashMap<>();

    // Last value of each param (for detecting changes in value)
    final String yamcsInstance;
    static private final Logger log = LoggerFactory.getLogger(AlarmServer.class);

    private List<AlarmListener<T>> alarmListeners = new CopyOnWriteArrayList<>();

    /**
     * 
     * @param yamcsInstance
     */
    public AlarmServer(String yamcsInstance) {
        this.yamcsInstance = yamcsInstance;
    }

    /**
     * Register for alarm notices
     * 
     * @return the current set of active alarms
     */
    public Map<S, ActiveAlarm<T>> subscribeAlarm(AlarmListener<T> listener) {
        alarmListeners.add(listener);
        return activeAlarms;
    }

    public void unsubscribeAlarm(AlarmListener<T> listener) {
        alarmListeners.remove(listener);
    }

    /**
     * Returns the current set of active alarms
     */
    public Map<S, ActiveAlarm<T>> getActiveAlarms() {
        return activeAlarms;
    }

    @Override
    public void doStart() {
        notifyStarted();
    }

    public void update(T pv, int minViolations) {
        update(pv, minViolations, false);
    }

    public void update(T value, int minViolations, boolean autoAck) {
        S alarmId = getObjectId(value);

        ActiveAlarm<T> activeAlarm = activeAlarms.get(alarmId);

        boolean noAlarm = isOkNoAlarm(value);

        if (noAlarm) {
            if (activeAlarm == null) {
                return;
            }

            if (activeAlarm.violations < minViolations) {
                log.debug("Clearing glitch for {}", getName(alarmId));
                activeAlarms.remove(alarmId);
                return;
            }

            activeAlarm.currentValue = value;
            if ((activeAlarm.acknowledged) || (activeAlarm.autoAcknowledge)) {
                for (AlarmListener<T> l : alarmListeners) {
                    l.notifyCleared(activeAlarm);
                }
                activeAlarms.remove(alarmId);
            } else {
                activeAlarm.valueCount++;
                for (AlarmListener<T> l : alarmListeners) {
                    l.notifyValueUpdate(activeAlarm);
                }
            }

        } else { // alarm
            if (activeAlarm == null) {
                activeAlarm = new ActiveAlarm<T>(value, autoAck);
                activeAlarms.put(alarmId, activeAlarm);
            } else {
                activeAlarm.currentValue = value;
                activeAlarm.violations++;
                activeAlarm.valueCount++;
            }
            if (activeAlarm.violations < minViolations) {
                return;
            }

            if (!activeAlarm.triggered) {
                activeAlarm.triggered = true;
                for (AlarmListener<T> l : alarmListeners) {
                    l.notifyTriggered(activeAlarm);
                }
            } else {
                if (moreSevere(value, activeAlarm.mostSevereValue)) {
                    activeAlarm.mostSevereValue = value;
                    for (AlarmListener<T> l : alarmListeners) {
                        l.notifySeverityIncrease(activeAlarm);
                    }
                }
                for (AlarmListener<T> l : alarmListeners) {
                    l.notifyValueUpdate(activeAlarm);
                }
            }

            activeAlarms.put(alarmId, activeAlarm);
        }
    }

    private S getObjectId(T value) {
        if (value instanceof ParameterValue) {
            return (S) ((ParameterValue) value).getParameter();
        } else if (value instanceof Event) {
            Event ev = (Event) value;
            return (S) new EventId(ev.getSource(), ev.getType());
        } else {
            throw new IllegalArgumentException("Unknonw object type " + value.getClass());
        }
    }

    static private String getName(Object objectId) {
        if (objectId instanceof Parameter) {
            return ((Parameter) objectId).getQualifiedName();
        } else if (objectId instanceof EventId) {
            EventId eid = (EventId) objectId;
            return eid.source + "." + eid.type;
        } else {
            throw new IllegalStateException();
        }
    }

    static private boolean isOkNoAlarm(Object value) {
        if (value instanceof ParameterValue) {
            ParameterValue pv = (ParameterValue) value;
            return (pv.getMonitoringResult() == MonitoringResult.IN_LIMITS
                    || pv.getMonitoringResult() == null
                    || pv.getMonitoringResult() == MonitoringResult.DISABLED);
        } else if (value instanceof Event) {
            Event ev = (Event) value;
            return ev.getSeverity() == null || ev.getSeverity() == EventSeverity.INFO;
        } else {
            throw new IllegalStateException("Unknown value " + value.getClass());
        }

    }

    public ActiveAlarm<T> acknowledge(S objectId, int id, String username, long ackTime, String message)
            throws CouldNotAcknowledgeAlarmException {
        ActiveAlarm<T> aa = activeAlarms.get(objectId);
        if (aa == null) {
            throw new CouldNotAcknowledgeAlarmException(objectId + " is not in state of alarm");
        }
        if (aa.id != id) {
            log.warn("Got acknowledge for {} but the id does not match", objectId);
            throw new CouldNotAcknowledgeAlarmException(
                    "Alarm Id " + id + " does not match parameter " + objectId);
        }

        aa.acknowledged = true;
        aa.usernameThatAcknowledged = username;
        aa.acknowledgeTime = ackTime;
        aa.message = message;
        alarmListeners.forEach(l -> l.notifyAcknowledged(aa));
        if (isOkNoAlarm(aa.currentValue)) {
            activeAlarms.remove(objectId);
            alarmListeners.forEach(l -> l.notifyCleared(aa));
        }

        return aa;
    }

    protected static boolean moreSevere(Object newValue, Object oldValue) {
        if (newValue instanceof ParameterValue) {
            return moreSevere(((ParameterValue) newValue).getMonitoringResult(),
                    ((ParameterValue) oldValue).getMonitoringResult());
        } else if (newValue instanceof Event) {
            return moreSevere(((Event) newValue).getSeverity(), ((Event) oldValue).getSeverity());
        } else {
            throw new IllegalStateException();
        }
    }

    protected static boolean moreSevere(MonitoringResult mr1, MonitoringResult mr2) {
        return mr1.getNumber() > mr2.getNumber();
    }

    protected static boolean moreSevere(EventSeverity es1, EventSeverity es2) {
        return es1.getNumber() > es2.getNumber();
    }

    @Override
    public void doStop() {
        notifyStopped();
    }

    /**
     * The id of an event for an alarm is its (source,type)
     * <p>
     * This means if an alarm is active and an event is generated with the same (source,type) a new alarm will not be
     * created but the old one updated
     * 
     * @author nm
     *
     */
    public static class EventId {
        final String source;
        final String type;

        public EventId(String source, String type) {
            this.source = source;
            this.type = type;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((source == null) ? 0 : source.hashCode());
            result = prime * result + ((type == null) ? 0 : type.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            EventId other = (EventId) obj;

            if (source == null) {
                if (other.source != null)
                    return false;
            } else if (!source.equals(other.source))
                return false;
            if (type == null) {
                if (other.type != null)
                    return false;
            } else if (!type.equals(other.type))
                return false;
            return true;
        }

    }
}
