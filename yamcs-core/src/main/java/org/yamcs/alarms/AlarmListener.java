package org.yamcs.alarms;

public interface AlarmListener {
    
    public void notifyTriggered(ActiveAlarm activeAlarm) ;
    public void notifySeverityIncrease(ActiveAlarm activeAlarm);    
    public void notifyParameterValueUpdate(ActiveAlarm activeAlarm);
    public void notifyAcknowledged(ActiveAlarm activeAlarm);    
    public void notifyCleared(ActiveAlarm activeAlarm);
}
