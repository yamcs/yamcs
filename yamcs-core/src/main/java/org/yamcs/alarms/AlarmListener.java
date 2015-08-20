package org.yamcs.alarms;

public interface AlarmListener {
    
    public void notifySeverityIncrease(ActiveAlarm activeAlarm);
    public void notifyTriggered(ActiveAlarm activeAlarm) ;
    public void notifyUpdate(ActiveAlarm activeAlarm);
    /**
     * 
     * @param activeAlarm
     * @param username - username that cleared the alarm or "autoCleared" if it's auto cleared
     */
    public void notifyCleared(ActiveAlarm activeAlarm);
}
