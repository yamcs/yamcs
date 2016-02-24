package org.yamcs.alarms;

import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.utils.TimeEncoding;

public class ActiveAlarm {
    
    static AtomicInteger counter = new AtomicInteger();
    public int id;
    
    public boolean acknowledged;
    
    public boolean autoAcknowledge;
    
    public long acknowledgeTime = TimeEncoding.INVALID_INSTANT;
    
    //the value that triggered the alarm
    public ParameterValue triggerValue;
    
    //most severe value
    public ParameterValue mostSevereValue;
    
    //current value of the parameter
    public ParameterValue currentValue;
    
    //message provided at triggering time  
    public String message;
    
    public int violations=1;
    
    public String usernameThatAcknowledged;
    
    
    public ActiveAlarm(ParameterValue pv) {
        this.triggerValue = this.currentValue = this.mostSevereValue = pv;
        id = counter.getAndIncrement();
    }
    
    public ActiveAlarm(ParameterValue pv, boolean autoAck) {
        this(pv);
        this.autoAcknowledge = autoAck;
    }
}
