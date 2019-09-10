package org.yamcs.alarms;

import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.utils.TimeEncoding;

/**
 * Keeps track of the alarm for one parameter or event.
 * <p>
 * This will only exist for an alarm that has been triggered. A parameter that has limits definition but never had an
 * out of limits value, will not have an active alarm.
 * <p>
 * Note: generics parameter T can effectively be either {@link ParameterValue} or {@link Event}
 * 
 * 
 * @author nm
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

    public long acknowledgeTime = TimeEncoding.INVALID_INSTANT;

    public long clearTime = TimeEncoding.INVALID_INSTANT;

    // the value that triggered the alarm
    public T triggerValue;

    // most severe value
    public T mostSevereValue;

    // current value of the parameter
    public T currentValue;

    // message provided at acknowledge time
    private String acknowledgeMessage;

    // message provided at clear time
    public String clearMessage;

    public int violations = 1;
    public int valueCount = 1;

    public String usernameThatAcknowledged;

    public String usernameThatCleared;

    boolean shelved;
    private String usernameThatShelved;

    private long shelveTime;
    private String shelveMessage;
    private long shelveDuration;

    public ActiveAlarm(T pv, boolean autoAck, boolean latching) {
        this.autoAcknowledge = autoAck;
        this.latching = latching;

        this.triggerValue = this.currentValue = this.mostSevereValue = pv;
        id = counter.getAndIncrement();
    }

    public ActiveAlarm(T pv) {
        this(pv, false, false);
    }

    public boolean isAutoAcknowledge() {
        return autoAcknowledge;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(boolean acknowledged) {
        this.acknowledged = acknowledged;
    }

    public int getId() {
        return id;
    }

    public String getAckMessage() {
        return acknowledgeMessage;
    }

    public void setAckMessage(String ackMessage) {
        this.acknowledgeMessage = ackMessage;
    }

    public boolean triggered() {
        return triggered;
    }

    public synchronized void clear(String username, long time, String message) {
        this.usernameThatCleared = username;
        this.clearTime = time;
        this.clearMessage = message;
        this.processOK = true;
        this.triggered = false;
        this.acknowledged = true;
    }

    /**
     * Trigger the alarm if not already triggered
     * 
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
        this.acknowledged = true;

        this.acknowledgeMessage = message;
        this.usernameThatAcknowledged = username;
        this.acknowledgeTime = ackTime;
    }

    /**
     * Called when the process returns to normal (i.e. parameter is back in limits)
     * 
     * @return true if the alarm has been updated
     */
    public synchronized boolean processRTN() {
        if (processOK) {
            return false;
        }

        processOK = true;

        if (!latching) {
            triggered = false;
        }
        if (autoAcknowledge) {
            acknowledgeTime = TimeEncoding.getWallclockTime();
            acknowledgeMessage = "auto-acknowledged";
            acknowledged = true;
        }
        return true;
    }

    /**
     * Called when the operator resets a latching alarm
     */
    public synchronized void reset() {
        triggered = processOK;
    }

    public synchronized void shelve(String username, String message, long shelveDuration) {
        this.shelved = true;
        this.usernameThatShelved = username;
        this.shelveTime = TimeEncoding.getWallclockTime();
        this.shelveMessage = message;
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

    public void setShelveTime(long shelveTime) {
        this.shelveTime = shelveTime;
    }

    public String getShelveMessage() {
        return shelveMessage;
    }

    public void setShelveMessage(String shelveMessage) {
        this.shelveMessage = shelveMessage;
    }

    public long getShelveDuration() {
        return shelveDuration;
    }

    public void setShelveDuration(long shelveDuration) {
        this.shelveDuration = shelveDuration;
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
        return clearTime;
    }

    public String getClearMessage() {
        return clearMessage;
    }

    public String getUsernameThatShelved() {
        return usernameThatShelved;
    }

    public void setUsernameThatShelved(String usernameThatShelved) {
        this.usernameThatShelved = usernameThatShelved;
    }

    public String getUsernameThatCleared() {
        return usernameThatCleared;
    }
}
