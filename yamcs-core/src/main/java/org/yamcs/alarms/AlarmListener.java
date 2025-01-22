package org.yamcs.alarms;

public interface AlarmListener<T> {
    public void notifyUpdate(AlarmNotificationType notificationType, ActiveAlarm<T> activeAlarm) ;
    public void notifySeverityIncrease(ActiveAlarm<T> activeAlarm);    
    public void notifyValueUpdate(ActiveAlarm<T> activeAlarm);

    /**
     * Called when the alarm server is shutting down.
     */
    default void notifyShutdown(ActiveAlarm<T> alarm) {
    }
}
