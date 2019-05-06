package org.yamcs.alarms;

import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.utils.TimeEncoding;

/**
 * Keeps track of the alarm for one parameter or event.
 * <p>
 * Note: generics parameter T can effectively be either {@link ParameterValue} or {@link Event}
 * 
 * @author nm
 *
 */
public class ActiveAlarm<T> {

    static AtomicInteger counter = new AtomicInteger();
    public int id;

    /**
     * triggered will become true when the violations>= minViolations
     */
    boolean triggered;

    public boolean acknowledged;

    public boolean autoAcknowledge;

    public long acknowledgeTime = TimeEncoding.INVALID_INSTANT;

    // the value that triggered the alarm
    public T triggerValue;

    // most severe value
    public T mostSevereValue;

    // current value of the parameter
    public T currentValue;

    // message provided at triggering time
    public String message;

    public int violations = 1;
    public int valueCount = 1;

    public String usernameThatAcknowledged;

    public ActiveAlarm(T pv) {
        this.triggerValue = this.currentValue = this.mostSevereValue = pv;
        id = counter.getAndIncrement();
    }

    public ActiveAlarm(T pv, boolean autoAck) {
        this(pv);
        this.autoAcknowledge = autoAck;
    }
}
