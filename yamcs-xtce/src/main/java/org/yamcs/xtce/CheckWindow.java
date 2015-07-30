package org.yamcs.xtce;

import java.io.Serializable;

/**
 * From XTCE: Holds a time to stop checking and optional time to start checking and whether window is relative to command release or last verifier.
 * 
 * @author nm
 *
 */
public class CheckWindow implements Serializable{
    private static final long serialVersionUID = 1L;
    
    
    public enum TimeWindowIsRelativeToType {commandRelease, timeLastVerifierPassed};
    
   
    final private long timeToStartChecking; //time to start checking in milliseconds (if -1 - it means not defined)
    final private long timeToStopChecking; //time to start checking in milliseconds
    
    final private TimeWindowIsRelativeToType timeWindowIsRelativeTo;
    
    public CheckWindow(long timeToStartChecking, long timeToStopChecking,  TimeWindowIsRelativeToType timeWindowIsRelativeTo) {
        super();
        this.timeToStartChecking = timeToStartChecking;
        this.timeToStopChecking = timeToStopChecking;
        this.timeWindowIsRelativeTo = timeWindowIsRelativeTo;
    }

    public long getTimeToStartChecking() {
        return timeToStartChecking;
    }

    public long getTimeToStopChecking() {
        return timeToStopChecking;
    }

    public TimeWindowIsRelativeToType getTimeWindowIsRelativeTo() {
        return timeWindowIsRelativeTo;
    }

    public boolean hasStart() {
        return timeToStartChecking!=-1;
    }
    
    
    public String toString() {
        return timeWindowIsRelativeTo+"["+timeToStartChecking+","+timeToStopChecking+"]";
    }
}
