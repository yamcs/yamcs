package org.yamcs.alarms;

import static org.yamcs.alarms.AlarmStreamer.CNAME_LAST_VALUE;
import static org.yamcs.alarms.AlarmStreamer.CNAME_VALUE_COUNT;
import static org.yamcs.alarms.AlarmStreamer.CNAME_VIOLATION_COUNT;

import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;

public abstract class AbstractAlarmMirrorServer<S, T> extends AbstractAlarmServer<S, T> {

    AbstractAlarmMirrorServer(String yamcsInstance, double alarmLoadDays) {
        super(yamcsInstance);
        if (alarmLoadDays > 0) {
            loadAlarmsFromDb(alarmLoadDays, activeAlarms);
        }
        log.info("Restored {} alarms from the database", activeAlarms.size());
    }

    void processTuple(Stream stream, Tuple tuple) {
        S subject = getSubject(tuple);

        var activeAlarm = activeAlarms.get(subject);
        if (activeAlarm == null) {
            // first see if we have enough information in the tuple to make the alarm
            activeAlarm = createNewAlarm(subject, tuple);
            if (activeAlarm != null) {
                activeAlarms.put(subject, activeAlarm);
                if (activeAlarm.isPending()) {
                    notifyUpdate(AlarmNotificationType.TRIGGERED_PENDING, activeAlarm);
                } else {
                    notifyUpdate(AlarmNotificationType.TRIGGERED, activeAlarm);
                }
            } else {
                log.info("Ignoring tuple as no active or restored alarm has been found: {}", tuple);
            }
            return;
        }

        if (tuple.hasColumn(AlarmStreamer.CNAME_VIOLATION_COUNT)) {
            activeAlarm.setViolations(tuple.getIntColumn(AlarmStreamer.CNAME_VIOLATION_COUNT));
        }

        if (tuple.hasColumn(getColNameLastEvent())) {
            var notificationType = AlarmNotificationType.valueOf(
                    tuple.getColumn(getColNameLastEvent()));
            switch (notificationType) {
            case TRIGGERED:
                activeAlarm.trigger();
                break;
            case ACKNOWLEDGED:
                long ackTime = tuple.getTimestampColumn(AlarmStreamer.CNAME_ACK_TIME);
                activeAlarm.acknowledge(tuple.getColumn(AlarmStreamer.CNAME_ACK_BY), ackTime,
                        tuple.getColumn(AlarmStreamer.CNAME_ACK_MSG));
                notifyUpdate(notificationType, activeAlarm);
                break;
            case CLEARED:
                long clearTime = tuple.getTimestampColumn(AlarmStreamer.CNAME_CLEARED_TIME);
                String clearedBy = tuple.getColumn(AlarmStreamer.CNAME_CLEARED_BY);
                String clearMessage = tuple.getColumn(AlarmStreamer.CNAME_CLEAR_MSG);
                activeAlarm.clear(clearedBy, clearTime, clearMessage);
                activeAlarms.remove(subject);
                notifyUpdate(notificationType, activeAlarm);
                break;
            case RESET:
                // TODO reset not yet implemented in the AlarmServer
                notifyUpdate(notificationType, activeAlarm);
                break;
            case RTN:
                notifyUpdate(notificationType, activeAlarm);
                break;
            case SEVERITY_INCREASED:
                processSeverityIncrease(subject, activeAlarm, tuple);
                notifySeverityIncrease(activeAlarm);
                break;
            case SHELVED:
                long shelveTime = tuple.getTimestampColumn(AlarmStreamer.CNAME_SHELVED_TIME);
                activeAlarm.shelve(shelveTime, tuple.getColumn(AlarmStreamer.CNAME_SHELVED_BY),
                        tuple.getColumn(AlarmStreamer.CNAME_SHELVED_MSG),
                        tuple.getLongColumn(AlarmStreamer.CNAME_SHELVE_DURATION));
                notifyUpdate(notificationType, activeAlarm);
                break;
            case UNSHELVED:
                activeAlarm.unshelve();
                notifyUpdate(notificationType, activeAlarm);
                break;
            case VALUE_UPDATED:
                processValueUpdate(subject, activeAlarm, tuple);
                notifyValueUpdate(activeAlarm);
                break;
            default:
                log.warn("Unexpected alarm notification type {}", notificationType);
                break;

            }
        } else {
            // yamcs older than 5.11
            processValueUpdate(subject, activeAlarm, tuple);
            notifyValueUpdate(activeAlarm);
        }
    }

    protected void processValueUpdate(S subject, ActiveAlarm<T> activeAlarm, Tuple tuple) {
        activeAlarm.setViolations(tuple.getIntColumn(CNAME_VIOLATION_COUNT));
        activeAlarm.setValueCount(tuple.getIntColumn(CNAME_VALUE_COUNT));
        activeAlarm.setCurrentValue(tuple.getColumn(CNAME_LAST_VALUE));
    }

    protected void processSeverityIncrease(S subject, ActiveAlarm<T> activeAlarm, Tuple tuple) {
        activeAlarm.setMostSevereValue(tuple.getColumn(getColNameSeverityIncreased()));
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }

    abstract S getSubject(Tuple tuple);

    protected abstract ActiveAlarm<T> createNewAlarm(S subject, Tuple tuple);

    @Override
    protected abstract String getColNameLastEvent();

    @Override
    protected abstract String getColNameSeverityIncreased();
}
