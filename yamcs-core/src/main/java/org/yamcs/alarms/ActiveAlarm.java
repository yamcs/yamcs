package org.yamcs.alarms;

import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.yarch.protobuf.Db.Event;
import org.yamcs.utils.TimeEncoding;

/**
 * Keeps track of the alarm for one parameter or event.
 * <p>
 * This will only exist for an alarm that has been triggered. A parameter that has limits definition but never had an
 * out of limits value, will not have an active alarm.
 * <p>
 * Note: generics parameter T can effectively be either {@link ParameterValue} or {@link Event}
 * 
 */
public class ActiveAlarm<T> {
    static AtomicInteger counter = new AtomicInteger();
    /**
     * If the alarm should auto acknowledge once the values are back within limits
     */
    final boolean autoAcknowledge;

    /**
     * Latching means that the alarm will stay triggered even if the value comes back within limits
     */
    final boolean latching;

    /**
     * unique identifier for the alarm (used to store it in the database also for the REST API)
     */
    private final int id;

    /**
     * If the process that generates the alarm is OK or not (i.e. if the latest value of the parameter is within limits)
     */
    boolean processOK = true;
    /**
     * If the alarm is latching triggered will stay true even when processOK becomes true
     */
    boolean triggered = false;

    /**
     * If a user has acknowledged the alarm
     */
    boolean acknowledged = true;

    // the value that triggered the alarm
    private T triggerValue;

    // most severe value
    private T mostSevereValue;

    // current value of the parameter
    private T currentValue;

    private long shelveTime;

    private int violations = 1;
    private int valueCount = 1;

    ChangeEvent ackEvent;
    ChangeEvent clearEvent;
    ChangeEvent resetEvent;
    ChangeEvent shelveEvent;

    boolean shelved;
    private long shelveDuration;

    ActiveAlarm(T pv, boolean autoAck, boolean latching, int id) {
        this.autoAcknowledge = autoAck;
        this.latching = latching;

        this.triggerValue = this.currentValue = this.setMostSevereValue(pv);
        this.id = id;
    }

    public ActiveAlarm(T pv, boolean autoAck, boolean latching) {
        this(pv, autoAck, latching, counter.getAndIncrement());
    }

    public boolean isAutoAcknowledge() {
        return autoAcknowledge;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public int getId() {
        return id;
    }

    public String getAckMessage() {
        return ackEvent == null ? null : ackEvent.message;
    }

    public boolean triggered() {
        return triggered;
    }

    public synchronized void clear(String username, long time, String message) {
        this.processOK = true;
        this.triggered = false;
        this.acknowledged = true;
        this.clearEvent = new ChangeEvent(username, time, message);
    }

    /**
     * Trigger the alarm if not already triggered
     */
    public synchronized void trigger() {
        if (!triggered) {
            processOK = false;
            triggered = true;
            acknowledged = false;
        }
    }

    /**
     * Acknowledge the alarm. This method does nothing if the alarm is already acknowledged.
     * 
     * @param username
     * @param ackTime
     * @param message
     */
    public synchronized void acknowledge(String username, long ackTime, String message) {
        if (acknowledged) {
            return;
        }
        this.acknowledged = true;

        this.ackEvent = new ChangeEvent(username, ackTime, message);

        if (isNormal()) {
            this.clearEvent = new ChangeEvent("yamcs", ackTime, "cleared due to ack");
        }

    }

    /**
     * Called when the process returns to normal (i.e. parameter is back in limits)
     * 
     * @return true if the alarm has been updated
     */
    public synchronized boolean processRTN(long time) {
        if (processOK) {
            return false;
        }

        processOK = true;
        if (!latching) {
            triggered = false;
        }
        if (autoAcknowledge) {
            this.ackEvent = new ChangeEvent("yamcs", time, "auto-acknowledged");
            acknowledged = true;
        }
        if (!triggered && acknowledged) {
            new ChangeEvent("yamcs", time, "cleared due to ack");
        }

        return true;
    }

    /**
     * Called when the operator resets a latching alarm
     */
    public synchronized void reset(String username, long time, String message) {
        triggered = processOK;
        this.resetEvent = new ChangeEvent(username, time, message);
    }

    public synchronized void shelve(String username, String message, long shelveDuration) {
        shelve(TimeEncoding.getWallclockTime(), username, message, shelveDuration);
    }

    public synchronized void shelve(long shelveTime, String username, String message, long shelveDuration) {
        this.shelved = true;
        this.shelveEvent = new ChangeEvent(username, TimeEncoding.getWallclockTime(), message);
        this.shelveTime = shelveTime;
        this.shelveDuration = shelveDuration;
    }

    public boolean isShelved() {
        return shelved;
    }

    public void unshelve() {
        this.shelved = false;
    }

    public String getShelveUsername() {
        return getUsernameThatShelved();
    }

    public long getShelveTime() {
        return shelveTime;
    }

    public String getShelveMessage() {
        return shelveEvent == null ? null : shelveEvent.message;
    }

    public long getShelveDuration() {
        return shelveDuration;
    }

    public boolean isNormal() {
        return processOK && !triggered && acknowledged;
    }

    public long getShelveExpiration() {
        if (shelveDuration == -1) {
            return -1;
        } else {
            return shelveTime + shelveDuration;
        }
    }

    public boolean isProcessOK() {
        return processOK;
    }

    public boolean isTriggered() {
        return triggered;
    }

    public long getClearTime() {
        return clearEvent == null ? TimeEncoding.INVALID_INSTANT : clearEvent.time;
    }

    public String getUsernameThatShelved() {
        return shelveEvent == null ? null : shelveEvent.username;
    }

    public String getClearMessage() {
        return clearEvent == null ? null : clearEvent.message;
    }

    public String getUsernameThatCleared() {
        return clearEvent == null ? null : clearEvent.username;
    }

    public String getUsernameThatAcknowledged() {
        return ackEvent == null ? null : ackEvent.username;
    }

    public long getAcknowledgeTime() {
        return ackEvent == null ? TimeEncoding.INVALID_INSTANT : ackEvent.time;
    }

    public T getTriggerValue() {
        return triggerValue;
    }

    public T getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(T value) {
        this.currentValue = value;
    }

    public T getMostSevereValue() {
        return mostSevereValue;
    }

    public void incrementValueCount() {
        valueCount++;
    }

    public int getValueCount() {
        return valueCount;
    }

    public void incrementViolations() {
        violations++;
    }

    public int getViolations() {
        return violations;
    }

    void setViolations(int count) {
        this.violations = count;
    }

    @Override
    public String toString() {
        return "ActiveAlarm [autoAcknowledge=" + autoAcknowledge + ", latching=" + latching + ", id=" + id
                + ", processOK=" + processOK + ", triggered=" + triggered + ", ackEvent=" + ackEvent
                + ", clearEvent=" + clearEvent + ", triggerValue=" + triggerValue
                + ", mostSevereValue=" + getMostSevereValue() + ", currentValue=" + currentValue
                + ", violations=" + violations + ", valueCount=" + valueCount + ", usernameThatAcknowledged="
                + ", shelved=" + shelved + ", shelveEvent=" + shelveEvent + ", shelveTime=" + shelveTime
                + ", shelveDuration=" + shelveDuration + "]";
    }

    public T setMostSevereValue(T mostSevereValue) {
        this.mostSevereValue = mostSevereValue;
        return mostSevereValue;
    }

    static class ChangeEvent {
        final String username;
        final long time;
        final String message;

        public ChangeEvent(String username, long time, String message) {
            this.username = username;
            this.time = time;
            this.message = message;
        }

        public String toString() {
            return "[username: " + username + " time: " + TimeEncoding.toString(time) + " message: " + message + "]";
        }
    }

    void setAcknowledged(boolean ack) {
        this.acknowledged = ack;
    }
}
