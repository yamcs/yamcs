package org.yamcs.alarms;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ProcessorConfig;
import org.yamcs.YamcsServer;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.mdb.ParameterAlarmChecker;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Event.EventSeverity;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.protobuf.Db.Event;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import static org.yamcs.alarms.AlarmStreamer.*;

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
public abstract class AlarmServer<S, T> extends AbstractService {

    protected Map<S, ActiveAlarm<T>> activeAlarms = new ConcurrentHashMap<>();

    final String yamcsInstance;
    static private final Logger log = LoggerFactory.getLogger(AlarmServer.class);

    private CopyOnWriteArrayList<AlarmListener<T>> alarmListeners = new CopyOnWriteArrayList<>();
    final private ScheduledThreadPoolExecutor timer;
    final TimeService timeService;

    public AlarmServer(String yamcsInstance, ProcessorConfig procConfig, ScheduledThreadPoolExecutor timer) {
        this.yamcsInstance = yamcsInstance;
        this.timer = timer;
        this.timeService = YamcsServer.getTimeService(yamcsInstance);
        if (procConfig.getAlarmLoadDays() > 0) {
            loadAlarmsFromDb(procConfig.getAlarmLoadDays());
        }
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
        ActiveAlarm<T> alarm = activeAlarms.get(subject);
        if (alarm != null) {
            if (alarm.getId() != id) {
                throw new AlarmSequenceException(alarm.getId(), id);
            }
            return alarm;
        }
        return null;
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
        if (!activeAlarms.containsValue(alarm)) {
            return null;
        }

        alarm.acknowledge(username, ackTime, message);
        alarmListeners.forEach(l -> l.notifyUpdate(AlarmNotificationType.ACKNOWLEDGED, alarm));

        if (alarm.isNormal()) {
            S subject = getSubject(alarm.getTriggerValue());
            activeAlarms.remove(subject);
            alarmListeners.forEach(l -> l.notifyUpdate(AlarmNotificationType.CLEARED, alarm));
        }

        return alarm;
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
        if (!activeAlarms.containsValue(alarm)) {
            return null;
        }
        alarm.reset(username, resetTime, message);
        return alarm;
    }

    /**
     * Acknowledges an active alarm instance. If the alarm state is no longer applicable, the alarm is also cleared,
     * otherwise the alarm will remain active.
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
        if (!activeAlarms.containsValue(alarm)) {
            return null;
        }
        alarm.clear(username, clearTime, message);

        S subject = getSubject(alarm.getTriggerValue());
        activeAlarms.remove(subject);
        alarmListeners.forEach(l -> l.notifyUpdate(AlarmNotificationType.CLEARED, alarm));

        return alarm;
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
        if (!activeAlarms.containsValue(alarm)) {
            return null;
        }
        alarm.shelve(username, message, shelveDuration);
        alarmListeners.forEach(l -> l.notifyUpdate(AlarmNotificationType.SHELVED, alarm));
        timer.schedule(this::checkShelved, shelveDuration, TimeUnit.MILLISECONDS);

        return alarm;
    }

    private void checkShelved() {
        long t = TimeEncoding.getWallclockTime();

        for (ActiveAlarm<T> aa : activeAlarms.values()) {
            if (aa.isShelved()) {
                long exp = aa.getShelveExpiration();
                if (exp == -1) {
                    continue;
                }
                if (exp <= t) {
                    aa.unshelve();
                    alarmListeners.forEach(l -> l.notifyUpdate(AlarmNotificationType.UNSHELVED, aa));
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
        if (!activeAlarms.containsValue(alarm)) {
            return null;
        }
        alarm.unshelve();
        alarmListeners.forEach(l -> l.notifyUpdate(AlarmNotificationType.UNSHELVED, alarm));
        return alarm;
    }

    @Override
    public void doStart() {
        timer.execute(this::checkShelved);
        notifyStarted();
    }

    public void update(T pv, int minViolations) {
        update(pv, minViolations, false, false);
    }

    public void update(T value, int minViolations, boolean autoAck, boolean latching) {
        S alarmId = getSubject(value);

        ActiveAlarm<T> activeAlarm = activeAlarms.get(alarmId);

        boolean noAlarm = isOkNoAlarm(value);

        if (noAlarm) {
            if (activeAlarm == null) {
                return;
            }
            if (activeAlarm.isNormal()) {
                log.debug("Clearing glitch for {}", getName(alarmId));
                activeAlarms.remove(alarmId);
                return;
            }
            boolean updated = activeAlarm.processRTN(timeService.getMissionTime());

            activeAlarm.setCurrentValue(value);
            activeAlarm.incrementValueCount();
            for (AlarmListener<T> l : alarmListeners) {
                l.notifyValueUpdate(activeAlarm);
            }

            if (updated) {
                for (AlarmListener<T> l : alarmListeners) {
                    l.notifyUpdate(AlarmNotificationType.RTN, activeAlarm);
                }
                if (activeAlarm.isNormal()) {
                    activeAlarms.remove(alarmId);
                    if (activeAlarm.isNormal()) {
                        for (AlarmListener<T> l : alarmListeners) {
                            l.notifyUpdate(AlarmNotificationType.CLEARED, activeAlarm);
                        }
                    }
                }
            }
        } else { // alarm
            boolean newAlarm;
            if (activeAlarm == null) {
                activeAlarm = new ActiveAlarm<>(value, autoAck, latching);
                activeAlarms.put(alarmId, activeAlarm);
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
                activeAlarms.put(alarmId, activeAlarm);
                for (AlarmListener<T> l : alarmListeners) {
                    l.notifyUpdate(AlarmNotificationType.TRIGGERED, activeAlarm);
                }
            } else {
                if (moreSevere(value, activeAlarm.getMostSevereValue())) {
                    activeAlarm.setMostSevereValue(value);
                    for (AlarmListener<T> l : alarmListeners) {
                        l.notifySeverityIncrease(activeAlarm);
                    }
                } else {
                    for (AlarmListener<T> l : alarmListeners) {
                        l.notifyValueUpdate(activeAlarm);
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
    public void doStop() {
        notifyStopped();
    }

    /**
     * Removes all active alarms without acknowledgement !use only for unit tests!
     */
    public void clearAll() {
        activeAlarms.clear();
    }

    private void loadAlarmsFromDb(double numDays) {
        Mdb mdb = MdbFactory.getInstance(yamcsInstance);
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        String tblName = alarmTableName();
        var table = ydb.getTable(tblName);
        if (table == null) {
            log.debug("Cannot load alarms since table {} does not exist", tblName);
            return;
        }
        if (table.getColumnDefinition(getColNameLastEvent()) == null) {
            log.debug("Cannot load alarms since table {} does not have the column {} (probably it is empty)", tblName,
                    getColNameLastEvent());
            return;
        }
        StreamSqlResult result = null;
        try {
            long startTime = timeService.getMissionTime() - (long) (numDays * 24 * 3600_000);
            result = ydb.execute(
                    "select * from " + tblName + " where " + CNAME_TRIGGER_TIME + " > ? AND " + getColNameLastEvent()
                            + " != 'CLEARED'",
                    startTime);

            while (result.hasNext()) {
                var tuple = result.next();
                try {
                    addActiveAlarmFromTuple(mdb, tuple);
                } catch (Exception e) {
                    log.warn("Unable to load active alarm from tuple {}: {}", tuple, e);
                }
            }

        } catch (ParseException | StreamSqlException e) {
            throw new RuntimeException(e);
        } finally {
            if (result != null) {
                result.close();
            }
        }
    }

    protected abstract S getSubject(T value);

    protected abstract void addActiveAlarmFromTuple(Mdb mdb, Tuple t);

    protected abstract String alarmTableName();

    protected abstract String getColNameLastEvent();
}
