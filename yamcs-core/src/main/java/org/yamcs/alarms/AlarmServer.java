package org.yamcs.alarms;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ProcessorConfig;
import org.yamcs.mdb.ParameterAlarmChecker;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Event.EventSeverity;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.protobuf.Db.Event;

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
public abstract class AlarmServer<S, T> extends AbstractAlarmServer<S, T> {
    static private final Logger log = LoggerFactory.getLogger(AlarmServer.class);

    final private ScheduledThreadPoolExecutor timer;

    public AlarmServer(String yamcsInstance, ProcessorConfig procConfig, ScheduledThreadPoolExecutor timer) {
        super(yamcsInstance);
        this.timer = timer;
        if (procConfig.getAlarmLoadDays() > 0) {
            loadAlarmsFromDb(procConfig.getAlarmLoadDays(), activeAlarms);
        }
    }

    /**
     * Returns the active alarm for the specified {@code subject} if it also matches the specified {@code id}.
     * 
     * @param subject
     *            the subject to look for.
     * @param id
     *            the expected id of the active alarm.
     * @return the active alarm, or {@code null} if no alarm was found
     * @throws AlarmSequenceException
     *             when the specified id does not match the id of the active alarm
     */
    public ActiveAlarm<T> getActiveAlarm(S subject, int id) throws AlarmSequenceException {
        var lock = getLock(subject);
        synchronized (lock) {
            ActiveAlarm<T> alarm = activeAlarms.get(subject);
            if (alarm != null) {
                if (alarm.getId() != id) {
                    throw new AlarmSequenceException(alarm.getId(), id);
                }
                return alarm;
            }
            return null;
        }
    }

    /**
     * Returns the active alarm for the specified {@code subject}.
     * 
     * @param subject
     *            the subject to look for.
     * @return the active alarm, or {@code null} if no alarm was found
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
     * @return the updated alarm instance or null if the alarm was not found
     */
    public ActiveAlarm<T> acknowledge(ActiveAlarm<T> alarm, String username, long ackTime, String message) {
        var subject = getSubject(alarm.getTriggerValue());

        var lock = getLock(subject);
        synchronized (lock) {

            if (!activeAlarms.containsValue(subject)) {
                return null;
            }

            alarm.acknowledge(username, ackTime, message);
            notifyUpdate(AlarmNotificationType.ACKNOWLEDGED, alarm);

            if (alarm.isNormal()) {
                activeAlarms.remove(subject);
                notifyUpdate(AlarmNotificationType.CLEARED, alarm);
            }

            return alarm;
        }
    }

    /**
     * Reset a latched alarm
     * 
     * @param alarm
     * @param username
     * @param resetTime
     * @param message
     * @return the updated alarm instance or null if the alarm was not found
     */
    public ActiveAlarm<T> reset(ActiveAlarm<T> alarm, String username, long resetTime, String message) {
        S subject = getSubject(alarm.getTriggerValue());

        var lock = getLock(subject);
        synchronized (lock) {
            var alarm1 = activeAlarms.get(subject);
            if (alarm1 != alarm) {
                return null;
            }

            alarm.reset(username, resetTime, message);
            return alarm;
        }
    }

    /**
     * Clears an active alarm instance.
     * 
     * @param alarm
     *            the alarm to clear
     * @param username
     *            the user that cleared the alarm
     * @param message
     *            reason message. Leave <code>null</code> when no reason is given.
     * @return the updated alarm instance or null if the alarm was not found
     */
    public ActiveAlarm<T> clear(ActiveAlarm<T> alarm, String username, long clearTime, String message) {
        S subject = getSubject(alarm.getTriggerValue());
        var lock = getLock(subject);
        synchronized (lock) {
            if (!activeAlarms.remove(subject, alarm)) {
                return null;
            }

            alarm.clear(username, clearTime, message);
            notifyUpdate(AlarmNotificationType.CLEARED, alarm);

            return alarm;
        }
    }

    /**
     * Shelve an alarm
     * 
     * @param alarm
     * @param username
     * @param message
     * @param shelveDuration
     *            shelve duration in milliseconds
     * 
     * @return the updated alarm instance or null if the alarm was not found
     */
    public ActiveAlarm<T> shelve(ActiveAlarm<T> alarm, String username, String message,
            long shelveDuration) {
        S subject = getSubject(alarm.getTriggerValue());
        var lock = getLock(subject);

        synchronized (lock) {
            var alarm1 = activeAlarms.get(subject);
            if (alarm1 != alarm) {
                return null;
            }

            alarm.shelve(username, message, shelveDuration);
            notifyUpdate(AlarmNotificationType.SHELVED, alarm);
            timer.schedule(this::checkShelved, shelveDuration, TimeUnit.MILLISECONDS);

            return alarm;
        }
    }

    private void checkShelved() {
        long t = TimeEncoding.getWallclockTime();

        for (ActiveAlarm<T> alarm : activeAlarms.values()) {
            if (alarm.isShelved()) {
                long exp = alarm.getShelveExpiration();
                if (exp == -1) {
                    continue;
                }
                if (exp <= t) {
                    alarm.unshelve();
                    notifyUpdate(AlarmNotificationType.UNSHELVED, alarm);
                }
            }
        }
    }

    /**
     * Un-shelve an alarm
     * 
     * @param alarm
     * @param username
     * @return the updated alarm instance or null if the alarm was not found
     */
    public ActiveAlarm<T> unshelve(ActiveAlarm<T> alarm, String username) {
        S subject = getSubject(alarm.getTriggerValue());

        var lock = getLock(subject);
        synchronized (lock) {

            var alarm1 = activeAlarms.get(subject);
            if (alarm1 != alarm) {
                return null;
            }

            alarm.unshelve();
            notifyUpdate(AlarmNotificationType.UNSHELVED, alarm);
            return alarm;
        }
    }


    public void update(T pv, int minViolations) {
        update(pv, minViolations, false, false);
    }

    public void update(T value, int minViolations, boolean autoAck, boolean latching) {
        S subject = getSubject(value);
        var lock = getLock(subject);

        synchronized (lock) {
            ActiveAlarm<T> activeAlarm = activeAlarms.get(subject);

            boolean noAlarm = isOkNoAlarm(value);

            if (noAlarm) {
                if (activeAlarm == null) {
                    return;
                }
                if (activeAlarm.isNormal()) {
                    log.debug("Clearing glitch for {}", getName(subject));
                    activeAlarms.remove(subject);
                    return;
                }
                boolean updated = activeAlarm.processRTN(timeService.getMissionTime());

                activeAlarm.setCurrentValue(value);
                activeAlarm.incrementValueCount();
                notifyValueUpdate(activeAlarm);

                if (updated) {
                    notifyUpdate(AlarmNotificationType.RTN, activeAlarm);

                    if (activeAlarm.isNormal()) {
                        activeAlarms.remove(subject);
                        if (activeAlarm.isNormal()) {
                            notifyUpdate(AlarmNotificationType.CLEARED, activeAlarm);
                        }
                    }
                }
            } else { // alarm
                boolean newAlarm;
                if (activeAlarm == null) {
                    activeAlarm = new ActiveAlarm<>(value, autoAck, latching);
                    activeAlarms.put(subject, activeAlarm);
                    newAlarm = true;
                } else {
                    activeAlarm.setCurrentValue(value);
                    activeAlarm.incrementViolations();
                    activeAlarm.incrementValueCount();
                    newAlarm = false;
                }
                if (activeAlarm.getViolations() < minViolations) {
                    return;
                }
                activeAlarm.trigger();

                if (newAlarm) {
                    activeAlarms.put(subject, activeAlarm);
                    notifyUpdate(AlarmNotificationType.TRIGGERED, activeAlarm);
                } else {
                    if (moreSevere(value, activeAlarm.getMostSevereValue())) {
                        activeAlarm.setMostSevereValue(value);
                        notifySeverityIncrease(activeAlarm);
                    } else {
                        notifyValueUpdate(activeAlarm);
                    }
                }
            }
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
            return (pv.getMonitoringResult() == null
                    || pv.getMonitoringResult() == MonitoringResult.IN_LIMITS
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
    public void doStart() {
        timer.execute(this::checkShelved);
        notifyStarted();
    }

    @Override
    public void doStop() {
        // run the notifyShutdown in order to save the latest alarm information to the database
        for (var alarm : activeAlarms.values()) {
            alarmListeners.forEach(l -> l.notifyShutdown(alarm));
        }

        notifyStopped();
    }

    /**
     * Removes all active alarms without acknowledgement !use only for unit tests!
     */
    public void clearAll() {
        activeAlarms.clear();
    }
}
