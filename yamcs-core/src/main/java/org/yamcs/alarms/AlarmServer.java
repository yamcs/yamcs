package org.yamcs.alarms;

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
 * Specific functionality for each alarm type (e.g. disabling alarms) should be implemented in the respective
 * {@link ParameterAlarmChecker} or {@link EventAlarmServer}.
 * 
 */
public class AlarmServer<S, T> extends AbstractService {

    private Map<S, ActiveAlarm<T>> activeAlarms = new ConcurrentHashMap<>();

    final String yamcsInstance;
    static private final Logger log = LoggerFactory.getLogger(AlarmServer.class);

    private CopyOnWriteArrayList<AlarmListener<T>> alarmListeners = new CopyOnWriteArrayList<>();

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
    public Map<S, ActiveAlarm<T>> addAlarmListener(AlarmListener<T> listener) {
        alarmListeners.addIfAbsent(listener);
        return activeAlarms;
    }

    public void removeAlarmListener(AlarmListener<T> listener) {
        alarmListeners.remove(listener);
    }

    /**
     * Returns the current set of active alarms
     */
    public Map<S, ActiveAlarm<T>> getActiveAlarms() {
        return activeAlarms;
    }

    /**
     * Returns the active alarm for the specified <tt>subject</tt> if it also matches the specified <tt>id</tt>.
     * 
     * @param subject
     *            the subject to look for.
     * @param id
     *            the expected id of the active alarm.
     * @return the active alarm, or <tt>null</tt> if no alarm was found
     * @throws AlarmSequenceException
     *             when the specified id does not match the id of the active alarm
     */
    public ActiveAlarm<T> getActiveAlarm(S subject, int id) throws AlarmSequenceException {
        ActiveAlarm<T> alarm = activeAlarms.get(subject);
        if (alarm != null) {
            if (alarm.id != id) {
                throw new AlarmSequenceException(alarm.id, id);
            }
            return alarm;
        }
        return null;
    }

    /**
     * Returns the active alarm for the specified <tt>subject</tt>.
     * 
     * @param subject
     *            the subject to look for.
     * @return the active alarm, or <tt>null</tt> if no alarm was found
     */
    public ActiveAlarm<T> getActiveAlarm(S subject) {
        return activeAlarms.get(subject);
    }

    /**
     * Acknowledges an active alarm instance. If the alarm state is no longer applicable, the alarm is also cleared,
     * otherwise the alarm will remain active.
     * 
     * @param alarm
     *            the alarm to acknowledge
     * @param username
     *            the acknowledging user
     * @param ackTime
     *            the time associated with the acknowledgment
     * @param message
     *            reason message. Leave <code>null</code> when no reason is given.
     * @return the updated alarm instance
     * @throws CouldNotAcknowledgeAlarmException
     */
    public ActiveAlarm<T> acknowledge(ActiveAlarm<T> alarm, String username, long ackTime, String message)
            throws CouldNotAcknowledgeAlarmException {
        if (!activeAlarms.containsValue(alarm)) {
            throw new CouldNotAcknowledgeAlarmException("Alarm is not active");
        }

        alarm.acknowledged = true;
        alarm.usernameThatAcknowledged = username;
        alarm.acknowledgeTime = ackTime;
        alarm.message = message;
        alarmListeners.forEach(l -> l.notifyAcknowledged(alarm));
        if (isOkNoAlarm(alarm.currentValue)) {
            S subject = getSubject(alarm.triggerValue);
            activeAlarms.remove(subject);
            alarmListeners.forEach(l -> l.notifyCleared(alarm));
        }

        return alarm;
    }

    @Override
    public void doStart() {
        notifyStarted();
    }

    public void update(T pv, int minViolations) {
        update(pv, minViolations, false);
    }

    public void update(T value, int minViolations, boolean autoAck) {
        S alarmId = getSubject(value);

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
                activeAlarm = new ActiveAlarm<>(value, autoAck);
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

    @SuppressWarnings("unchecked")
    private S getSubject(T value) {
        if (value instanceof ParameterValue) {
            return (S) ((ParameterValue) value).getParameter();
        } else if (value instanceof Event) {
            Event ev = (Event) value;
            return (S) new EventId(ev.getSource(), ev.getType());
        } else {
            throw new IllegalArgumentException("Unknown object type " + value.getClass());
        }
    }

    static private String getName(Object subject) {
        if (subject instanceof Parameter) {
            return ((Parameter) subject).getQualifiedName();
        } else if (subject instanceof EventId) {
            EventId eid = (EventId) subject;
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
}
