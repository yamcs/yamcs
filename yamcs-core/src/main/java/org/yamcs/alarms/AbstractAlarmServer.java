package org.yamcs.alarms;

import static org.yamcs.alarms.AlarmStreamer.CNAME_TRIGGER_TIME;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.time.TimeService;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;

import com.google.common.util.concurrent.AbstractService;

public abstract class AbstractAlarmServer<S, T> extends AbstractService {
    final protected Log log;
    final protected String yamcsInstance;
    final protected TimeService timeService;

    Map<Stream, StreamSubscriber> susbscribers = new HashMap<>();

    // NUM_LOCKS has to be power of 2
    static final int NUM_LOCKS = 32;
    Object[] locks;

    protected Map<S, ActiveAlarm<T>> activeAlarms = new ConcurrentHashMap<>();
    protected CopyOnWriteArrayList<AlarmListener<T>> alarmListeners = new CopyOnWriteArrayList<>();

    public AbstractAlarmServer(String yamcsInstance) {
        this.yamcsInstance = yamcsInstance;
        this.timeService = YamcsServer.getTimeService(yamcsInstance);

        log = new Log(getClass(), yamcsInstance);

        locks = new Object[NUM_LOCKS];
        for (int i = 0; i < NUM_LOCKS; i++) {
            locks[i] = new Object();
        }
    }

    /**
     * Returns the current set of active alarms
     */
    public Map<S, ActiveAlarm<T>> getActiveAlarms() {
        return activeAlarms;
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

    void notifyUpdate(AlarmNotificationType notificationType, ActiveAlarm<T> alarm) {
        if (alarm.getTriggerValue() != null) {
            alarmListeners.forEach(l -> l.notifyUpdate(notificationType, alarm));
        } // else the alarm has never been triggered probably due to the minViolatios not being met
    }

    void notifySeverityIncrease(ActiveAlarm<T> alarm) {
        if (alarm.getTriggerValue() != null) {
            alarmListeners.forEach(l -> l.notifySeverityIncrease(alarm));
        } // else the alarm has never been triggered probably due to the minViolatios not being met
    }

    void notifyValueUpdate(ActiveAlarm<T> alarm) {
        if (alarm.getTriggerValue() != null) {
            alarmListeners.forEach(l -> l.notifyValueUpdate(alarm));
        } // else the alarm has never been triggered probably due to the minViolatios not being met
    }

    protected void loadAlarmsFromDb(double numDays, Map<S, ActiveAlarm<T>> alarms) {
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
                    addActiveAlarmFromTuple(mdb, tuple, alarms);
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

    protected Object getLock(S alarmId) {
        return locks[alarmId.hashCode() & (NUM_LOCKS - 1)];
    }

    protected abstract void addActiveAlarmFromTuple(Mdb mdb, Tuple t, Map<S, ActiveAlarm<T>> alarms);

    protected abstract S getSubject(T value);

    protected abstract String alarmTableName();

    protected abstract String getColNameLastEvent();

    protected abstract String getColNameSeverityIncreased();
}
