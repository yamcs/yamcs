package org.yamcs.alarms;

public interface AlarmListener<T> {
    
    public void notifyTriggered(ActiveAlarm<T> activeAlarm) ;
    public void notifySeverityIncrease(ActiveAlarm<T> activeAlarm);    
    public void notifyValueUpdate(ActiveAlarm<T> activeAlarm);
    public void notifyAcknowledged(ActiveAlarm<T> activeAlarm);    
    public void notifyCleared(ActiveAlarm<T> activeAlarm);
}
